import { DecimalPipe, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { forkJoin, map } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { errorMessage } from '../../core/errors';
import { Plan, ProblemDetail } from '../../core/models';
import { dateInZone, formatDateTime, formatTime, formatWindow } from '../../core/time';
import { Confirm } from '../../shared/confirm';
import { DateNav } from '../../shared/date-nav';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { MapDepot, MapRoute, MapStop, RouteMap } from '../../shared/map/route-map';
import { RouteView, buildPlanView, softCostLabel } from './plan-view';

interface SavedPlan { plan: Plan; solvedAt: string; publishedAt?: string; }

@Component({
  selector: 'app-planning',
  imports: [DecimalPipe, PercentPipe, RouterLink, Confirm, DateNav, Icon, LoadState, RouteMap],
  templateUrl: './planning.html',
  styleUrl: './planning.css',
})
export class Planning {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);

  protected readonly softCost = softCostLabel;

  protected readonly data = rxResource({
    params: () => ({ id: this.ctx.id(), date: this.ctx.date() }),
    stream: ({ params: { id, date } }) => forkJoin({
      // No date filter: orders with no window have no date, yet can still be delivered on this one
      orders: fetchAll((page) => this.api.orders(id, { status: 'pending', page, size: MAX_PAGE_SIZE })),
      shifts: fetchAll((page) => this.api.shifts(id, { date, page, size: MAX_PAGE_SIZE })),
      vehicles: fetchAll((page) => this.api.vehicles(id, { page, size: MAX_PAGE_SIZE })),
      depots: fetchAll((page) => this.api.depots(id, { page, size: MAX_PAGE_SIZE })),
    }).pipe(map((data) => ({
      ...data,
      candidates: data.orders.filter((order) => !order.windowStart || dateInZone(order.windowStart, this.ctx.zone()) === date),
    }))),
  });

  // Publishing turns the plan's orders from pending to planned, so after a reload they're no longer in the
  // list above. This loads them back for names and pins. params returning undefined keeps the resource idle.
  protected readonly plannedOrders = rxResource({
    params: () => (this.saved()?.plan.status === 'published' ? { id: this.ctx.id() } : undefined),
    // Once a driver starts a route its orders move from planned to out_for_delivery, so fetch both
    stream: ({ params: { id } }) => forkJoin((['planned', 'out_for_delivery'] as const).map((status) =>
      fetchAll((page) => this.api.orders(id, { status, page, size: MAX_PAGE_SIZE })))).pipe(map((lists) => lists.flat())),
  });

  // Orders on a published plan are planned now. Hiding them here rather than reloading keeps them in the
  // lookups, so the plan doesn't blink out while the planned orders load.
  private readonly publishedOrderIds = computed(() => new Set(this.saved()?.plan.status === 'published'
    ? this.saved()!.plan.routes.flatMap((route) => route.stops.map((stop) => stop.orderId)) : []));
  protected readonly candidates = computed(() => (this.data.hasValue()
    ? this.data.value().candidates.filter((order) => !this.publishedOrderIds().has(order.id)) : []));
  protected readonly shifts = computed(() => (this.data.hasValue() ? this.data.value().shifts : []));

  // Selection: everything is included until someone unticks it
  protected readonly excludedOrders = signal<ReadonlySet<number>>(new Set());
  protected readonly excludedShifts = signal<ReadonlySet<number>>(new Set());
  protected readonly selectedOrders = computed(() => this.candidates().filter((order) => !this.excludedOrders().has(order.id)));
  protected readonly selectedShifts = computed(() => this.shifts().filter((shift) => !this.excludedShifts().has(shift.id)));
  protected readonly selectedWeight = computed(() => this.selectedOrders().reduce((sum, order) => sum + order.totalWeightKg, 0));

  protected toggle(target: 'orders' | 'shifts', id: number, included: boolean): void {
    const set = target === 'orders' ? this.excludedOrders : this.excludedShifts;
    set.update((current) => {
      const next = new Set(current);
      if (included) next.delete(id); else next.add(id);
      return next;
    });
  }

  protected setAllOrders(included: boolean): void {
    this.excludedOrders.set(included ? new Set() : new Set(this.candidates().map((order) => order.id)));
  }

  // Solving
  protected readonly solving = signal(false);
  protected readonly elapsed = signal(0);
  protected readonly solveError = signal<string | null>(null);
  protected readonly saved = signal<SavedPlan | null>(null);
  protected readonly highlighted = signal<number | null>(null);
  private timer: ReturnType<typeof setInterval> | undefined;

  // The API saves each solved plan, but has no endpoint to read one back, so this browser keeps the copy it shows
  private readonly storageKey = computed(() => `route-scheduler.plan.${this.ctx.id()}.${this.ctx.date()}`);

  protected readonly published = computed(() => this.saved()?.plan.status === 'published');

  // A published plan stays on screen until the owner starts a new one, so solving can't replace it by accident
  protected readonly canSolve = computed(() =>
    !this.solving() && !this.published() && this.selectedOrders().length > 0 && this.selectedShifts().length > 0);

  protected readonly view = computed(() => {
    const saved = this.saved();
    if (!saved || !this.data.hasValue()) return null;
    const { orders: pending, shifts, vehicles, depots } = this.data.value();
    const orders = [...pending, ...(this.plannedOrders.value() ?? [])];

    // Wait for the planned orders rather than flash "Order #12" placeholders for a moment
    const known = new Set(orders.map((order) => order.id));
    const missing = saved.plan.routes.some((route) => route.stops.some((stop) => !known.has(stop.orderId)));
    if (missing && this.plannedOrders.isLoading()) return null;

    return buildPlanView(saved.plan, { orders, shifts, vehicles, depots }, this.ctx.zone());
  });
  protected readonly solvedAtLabel = computed(() => {
    const saved = this.saved();
    return saved ? formatDateTime(saved.solvedAt, this.ctx.zone()) : '';
  });
  protected readonly publishedAtLabel = computed(() => formatDateTime(this.saved()?.publishedAt, this.ctx.zone()));

  // Publishing: the API refuses while any route lacks a driver, so say which ones before the owner tries
  protected readonly publishing = signal(false);
  protected readonly publishError = signal<string | null>(null);
  protected readonly confirmPublish = signal(false);
  protected readonly routesWithoutDriver = computed(() => (this.view()?.routes ?? []).filter((route) => !route.driverName));
  protected readonly withoutDriverLabel = computed(() => {
    const names = this.routesWithoutDriver().map((route) => route.vehicleName);
    return `${listOf(names)} ${names.length === 1 ? 'has' : 'have'}`;
  });
  protected readonly publishMessage = computed(() => {
    const plan = this.view();
    if (!plan) return '';
    const drivers = [...new Set(plan.routes.map((route) => route.driverName))];
    const parts = [
      `${countOf(plan.routes.length, 'route')} ${plan.routes.length === 1 ? 'goes' : 'go'} to ${listOf(drivers)}, who will see ${drivers.length === 1 ? 'it' : 'them'} on My routes.`,
      `${countOf(plan.assignedCount, 'order')} on ${plan.routes.length === 1 ? 'it' : 'them'} ${plan.assignedCount === 1 ? 'becomes' : 'become'} planned, and can't be edited or put in another plan after this.`,
    ];
    if (plan.unassigned.length > 0) parts.push(`${countOf(plan.unassigned.length, 'order')} not on any route ${plan.unassigned.length === 1 ? 'stays' : 'stay'} pending.`);
    if (plan.score && !plan.score.feasible) parts.unshift('This plan breaks a hard rule.');
    return parts.join(' ');
  });

  // Map inputs: routes once solved, loose pins before that
  protected readonly mapRoutes = computed((): MapRoute[] => (this.view()?.routes ?? []).map((route) => ({
    id: route.shiftId,
    fill: route.fill,
    ink: route.ink,
    start: route.depot ? [route.depot.address.longitude, route.depot.address.latitude] : null,
    stops: route.stops.filter((stop) => stop.latitude !== null).map((stop) => ({
      label: String(stop.sequence + 1),
      title: stop.customerName,
      detail: `${formatTime(stop.arrival, this.ctx.zone())}, ${route.vehicleName}`,
      latitude: stop.latitude!,
      longitude: stop.longitude!,
    })),
    path: route.path,
  })));

  protected readonly mapLoose = computed((): MapStop[] => {
    const view = this.view();
    const orders = view
      ? view.unassigned.map((entry) => this.candidates().find((order) => order.id === entry.orderId)).filter((order) => !!order)
      : this.selectedOrders();
    return orders.map((order) => ({
      label: '',
      title: order.customer.name,
      detail: view ? 'Not on a route' : formatWindow(order.windowStart, order.windowEnd, this.ctx.zone()),
      latitude: order.address.latitude,
      longitude: order.address.longitude,
    }));
  });

  protected readonly mapDepots = computed((): MapDepot[] => {
    if (!this.data.hasValue()) return [];
    const { depots, vehicles } = this.data.value();
    const used = new Set(this.selectedShifts().map((shift) => vehicles.find((v) => v.id === shift.vehicleId)?.homeDepotId));
    return depots.filter((depot) => used.has(depot.id)).map((depot) => ({
      name: depot.name, latitude: depot.address.latitude, longitude: depot.address.longitude,
    }));
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => clearInterval(this.timer));

    // Each company and date has its own saved plan and its own selection
    effect(() => {
      const key = this.storageKey();
      this.saved.set(readSaved(key));
      this.excludedOrders.set(new Set());
      this.excludedShifts.set(new Set());
      this.solveError.set(null);
      this.highlighted.set(null);
      this.confirmPublish.set(false);
      this.publishError.set(null);
    });
  }

  protected solve(): void {
    if (!this.canSolve()) return;
    const key = this.storageKey();
    this.solving.set(true);
    this.solveError.set(null);
    this.elapsed.set(0);
    this.timer = setInterval(() => this.elapsed.update((seconds) => seconds + 1), 1000);

    const stop = () => {
      clearInterval(this.timer);
      this.solving.set(false);
    };

    this.publishError.set(null);
    this.api.solvePlan(this.ctx.id(), {
      planDate: this.ctx.date(),
      orderIds: this.selectedOrders().map((order) => order.id),
      shiftIds: this.selectedShifts().map((shift) => shift.id),
    }).subscribe({
      next: (plan) => {
        stop();
        const saved: SavedPlan = { plan, solvedAt: new Date().toISOString() };
        this.saved.set(saved);
        localStorage.setItem(key, JSON.stringify(saved));
      },
      error: (error: unknown) => {
        stop();
        this.solveError.set(errorMessage(error));
      },
    });
  }

  // The driver can open /route/{token} without logging in and accept or turn down the route there
  protected readonly copied = signal<number | null>(null);
  private copiedTimer: ReturnType<typeof setTimeout> | undefined;

  protected copyDriverLink(route: RouteView): void {
    const link = `${location.origin}/route/${route.shareToken}`;
    navigator.clipboard.writeText(link).then(
      () => {
        this.copied.set(route.routeId);
        clearTimeout(this.copiedTimer);
        this.copiedTimer = setTimeout(() => this.copied.set(null), 2500);
      },
      // Clipboard access can be refused (permissions, non-HTTPS host); the link is still one click away
      () => window.open(link, '_blank', 'noopener'),
    );
  }

  protected openPublish(): void {
    this.publishError.set(null);
    this.confirmPublish.set(true);
  }

  protected publish(): void {
    const saved = this.saved();
    if (!saved || this.publishing()) return;
    const key = this.storageKey();
    this.publishing.set(true);
    this.publishError.set(null);
    // The API accepts notifyDrivers but doesn't act on it yet (no notification channel); drivers check My routes
    this.api.publishPlan(saved.plan.planId, { notifyDrivers: true }).subscribe({
      next: (plan) => {
        this.publishing.set(false);
        this.confirmPublish.set(false);
        // The publish response leaves unassignedOrderIds empty, so keep the list from solving
        const next: SavedPlan = { ...saved, plan: { ...plan, unassignedOrderIds: saved.plan.unassignedOrderIds }, publishedAt: new Date().toISOString() };
        this.saved.set(next);
        localStorage.setItem(key, JSON.stringify(next));
      },
      error: (error: unknown) => {
        this.publishing.set(false);
        this.publishError.set(publishErrorMessage(error));
      },
    });
  }

  protected discardPlan(): void {
    localStorage.removeItem(this.storageKey());
    this.saved.set(null);
    this.highlighted.set(null);
  }

  // Template helpers
  protected time(instant: string | null): string { return formatTime(instant, this.ctx.zone()); }
  protected orderWindow(start: string | null, end: string | null): string { return formatWindow(start, end, this.ctx.zone()); }
  protected overloaded(route: RouteView): boolean { return (route.loadShare ?? 0) > 1; }
  protected meterWidth(route: RouteView): string { return `${Math.min(route.loadShare ?? 0, 1) * 100}%`; }
}

// A copy saved before the API stored plans has no planId, so it can't be published, and its times were
// written in the server's zone rather than the company's. Dropping it is safer than showing wrong times.
function readSaved(key: string): SavedPlan | null {
  try {
    const saved = JSON.parse(localStorage.getItem(key) ?? 'null') as SavedPlan | null;
    return saved?.plan?.planId ? saved : null;
  } catch {
    return null;
  }
}

function publishErrorMessage(error: unknown): string {
  const detail = error instanceof HttpErrorResponse && error.status === 409 ? (error.error as ProblemDetail | null)?.detail : undefined;
  // That 409 lists route ids, which mean nothing on this page. A route copies its shift's driver when it's
  // created, so the usual cause is a driver added to the shift after solving.
  if (detail?.startsWith('Assign a driver')) {
    return 'A route in this plan has no driver. If you added one on the Shifts page after solving, solve again so the plan picks them up.';
  }
  return errorMessage(error);
}

function countOf(count: number, noun: string): string {
  return `${count === 1 ? 'The' : `The ${count}`} ${noun}${count === 1 ? '' : 's'}`;
}

// ["Liam", "Ava", "Noah"] -> "Liam, Ava and Noah"
function listOf(names: (string | null)[]): string {
  const known = names.map((name) => name ?? 'a driver');
  return known.length <= 1 ? known.join('') : `${known.slice(0, -1).join(', ')} and ${known.at(-1)}`;
}
