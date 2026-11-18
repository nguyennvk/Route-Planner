import { DecimalPipe, PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin, map } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { errorMessage } from '../../core/errors';
import { Order, Plan, PlanSummary, PositionMessage, ProblemDetail, Shift } from '../../core/models';
import { companyLocalToInstant, dateInZone, formatDateTime, formatTime, formatWindow } from '../../core/time';
import { Confirm } from '../../shared/confirm';
import { DateNav } from '../../shared/date-nav';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { Live } from '../../core/live';
import { MapDepot, MapRoute, MapStop, MapVehicle, RouteMap } from '../../shared/map/route-map';
import { OrderDialog } from '../orders/order-dialog';
import { ShiftDialog } from '../shifts/shift-dialog';
import { PlanPreferences } from './plan-preferences';
import { DEFAULT_PLAN_SETTINGS, PlanSettings, readPlanSettings, settingsValid, toSolveRequest, writePlanSettings } from './plan-settings';
import { RouteView, buildPlanView, softCostLabel } from './plan-view';

// The plan on screen. solvedAt / publishedAt are instants, null until the plan list has them
interface SavedPlan { plan: Plan; solvedAt: string | null; publishedAt: string | null; }

@Component({
  selector: 'app-planning',
  imports: [DecimalPipe, PercentPipe, RouterLink, Confirm, DateNav, Icon, LoadState, OrderDialog, PlanPreferences, RouteMap, ShiftDialog],
  templateUrl: './planning.html',
  styleUrl: './planning.css',
})
export class Planning {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);
  private readonly live = inject(Live);

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
    params: () => (this.saved() && this.saved()!.plan.status !== 'draft' ? { id: this.ctx.id() } : undefined),
    // Drivers move orders on to out_for_delivery, then delivered or failed; an old plan from the history
    // can have orders in any of these. Cancelled ones stay on the route as skipped stops, so they're loaded too
    stream: ({ params: { id } }) => forkJoin((['planned', 'out_for_delivery', 'delivered', 'failed', 'cancelled'] as const).map((status) =>
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

  // Add or edit a shift or order without leaving the page. null keeps a dialog closed
  protected readonly shiftTarget = signal<Shift | 'new' | null>(null);
  protected readonly orderTarget = signal<Order | 'new' | null>(null);
  // A draft plan solved before an edit no longer matches what's ticked, so say so until it's solved again
  protected readonly changedSinceSolve = signal(false);

  protected shiftSaved(): void {
    this.shiftTarget.set(null);
    this.afterEdit();
  }

  protected orderSaved(): void {
    this.orderTarget.set(null);
    this.afterEdit();
  }

  // Reload the lists: a new shift or order shows up ticked, and an edited one shows its new details.
  // An order moved to another day drops off this day's list.
  private afterEdit(): void {
    if (this.saved()) this.changedSinceSolve.set(true);
    this.data.reload();
  }

  protected setAllOrders(included: boolean): void {
    this.excludedOrders.set(included ? new Set() : new Set(this.candidates().map((order) => order.id)));
  }

  // Costs and rules for the solver, one saved copy per company (loaded in the constructor's effect)
  protected readonly planSettings = signal<PlanSettings>(DEFAULT_PLAN_SETTINGS);
  protected readonly vehicleTypes = computed(() => (this.data.hasValue() ? [...new Set(this.data.value().vehicles.map((v) => v.type))] : []));
  // Only the fuel prices of the vehicles being solved need to be valid
  private readonly settingsReady = computed(() => {
    if (!this.data.hasValue()) return false;
    const typeById = new Map(this.data.value().vehicles.map((vehicle) => [vehicle.id, vehicle.type]));
    const types = [...new Set(this.selectedShifts().map((shift) => typeById.get(shift.vehicleId) ?? 'van'))];
    return settingsValid(this.planSettings(), types);
  });

  // Says why Solve is disabled when the only reason is a bad price
  protected readonly settingsBlocking = computed(() => this.data.hasValue() && !this.settingsReady());

  protected changeSettings(settings: PlanSettings): void {
    this.planSettings.set(settings);
    writePlanSettings(this.ctx.id(), settings);
  }

  // Solving
  protected readonly solving = signal(false);
  protected readonly elapsed = signal(0);
  protected readonly solveError = signal<string | null>(null);
  protected readonly highlighted = signal<number | null>(null);
  private timer: ReturnType<typeof setInterval> | undefined;

  // Every plan made for this day (a published one, drafts solved after it...), newest first
  protected readonly dayPlans = rxResource({
    params: () => ({ id: this.ctx.id(), date: this.ctx.date() }),
    stream: ({ params: { id, date } }) => this.api.plans(id, { date, size: 50 }).pipe(map((page) => page.content)),
  });
  // Only plans with something to show: not a failed solve, one still solving, or one where nothing fit
  protected readonly choosablePlans = computed(() =>
    (this.dayPlans.hasValue() ? this.dayPlans.value() : []).filter((plan) => plan.routeCount > 0 && plan.status !== 'failed' && plan.status !== 'solving'));

  // Which plan is on screen. undefined: the newest; 'new': none, to solve a fresh one; a number: that plan
  private readonly selection = signal<number | 'new' | undefined>(undefined);
  protected readonly selectedPlanId = computed(() => {
    const selection = this.selection();
    if (selection === 'new') return null;
    return typeof selection === 'number' ? selection : this.choosablePlans()[0]?.planId ?? null;
  });
  // A plan from the history page arrives as ?plan=12; applied once, when the page first loads its day
  private pendingPlanId: number | null = Number(inject(ActivatedRoute).snapshot.queryParamMap.get('plan')) || null;

  // Plans this visit solved or published. Only those responses know which orders were left out
  // (the API doesn't store that), so they're kept instead of loading the plan again
  private readonly freshPlans = signal<ReadonlyMap<number, Plan>>(new Map());
  protected readonly loadedPlan = rxResource({
    params: () => {
      const planId = this.selectedPlanId();
      return planId !== null && !this.freshPlans().has(planId) ? { planId } : undefined;
    },
    stream: ({ params: { planId } }) => this.api.plan(planId),
  });

  protected readonly saved = computed((): SavedPlan | null => {
    const planId = this.selectedPlanId();
    if (planId === null) return null;
    const loaded = this.loadedPlan.hasValue() ? this.loadedPlan.value() : undefined;
    const plan = this.freshPlans().get(planId) ?? (loaded?.planId === planId ? loaded : null);
    if (!plan) return null;
    const summary = this.dayPlans.hasValue() ? this.dayPlans.value().find((entry) => entry.planId === planId) : undefined;
    const zone = this.ctx.zone();
    return { plan, solvedAt: companyLocalToInstant(summary?.solvedAt ?? null, zone), publishedAt: companyLocalToInstant(summary?.publishedAt ?? null, zone) };
  });

  protected readonly published = computed(() => this.saved()?.plan.status === 'published');
  // Published or completed: either way it's gone out, so it can't be solved over
  protected readonly sent = computed(() => !!this.saved() && this.saved()!.plan.status !== 'draft');

  // A sent plan stays on screen until the owner starts a new one, so solving can't replace it by accident
  protected readonly canSolve = computed(() =>
    !this.solving() && !this.sent() && this.selectedOrders().length > 0 && this.selectedShifts().length > 0 && this.settingsReady());

  protected readonly view = computed(() => {
    const saved = this.saved();
    if (!saved || !this.data.hasValue()) return null;
    const { orders: pending, shifts, vehicles, depots } = this.data.value();
    const orders = [...pending, ...(this.plannedOrders.hasValue() ? this.plannedOrders.value() : [])];

    // Wait for the planned orders rather than flash "Order #12" placeholders for a moment
    const known = new Set(orders.map((order) => order.id));
    const missing = saved.plan.routes.some((route) => route.stops.some((stop) => !known.has(stop.orderId)));
    if (missing && this.plannedOrders.isLoading()) return null;

    return buildPlanView(saved.plan, { orders, shifts, vehicles, depots }, this.ctx.zone());
  });

  // Vans on the road. Kept out of view() on purpose: a position arrives every few seconds, and rebuilding
  // the whole plan (stops, loads, lines) for each one would redraw the map's lines and stops too.
  // Snapshot: where each van on a route in progress was last seen, loaded once per published plan
  protected readonly positionSnapshot = rxResource({
    params: () => (this.published() ? { planId: this.saved()!.plan.planId } : undefined),
    stream: ({ params: { planId } }) => this.api.planPositions(planId),
  });
  // Live: every message since the page subscribed, latest per route (filled by the effect in the constructor)
  private readonly livePositions = signal<ReadonlyMap<number, PositionMessage>>(new Map());

  // Latest known position per route: the snapshot, overridden by any newer live message
  protected readonly positions = computed(() => {
    const latest = new Map<number, { latitude: number; longitude: number; recordedAt: string }>();
    // hasValue() first: value() throws while the resource is in its error state
    for (const position of this.positionSnapshot.hasValue() ? this.positionSnapshot.value() : []) {
      if (position.routeId !== null) latest.set(position.routeId, position);
    }
    for (const position of this.livePositions().values()) {
      const known = latest.get(position.routeId);
      // Same-format wall-clock strings, so comparing them as text compares the times
      if (!known || position.recordedAt > known.recordedAt) latest.set(position.routeId, position);
    }
    return latest;
  });
  protected readonly solvedAtLabel = computed(() => formatDateTime(this.saved()?.solvedAt, this.ctx.zone()));
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
      // The vehicle is already clear from the pin's color and number
      detail: [
        stop.address,
        `Estimated arrival ${formatTime(stop.arrival, this.ctx.zone())}`,
        `Window ${formatWindow(stop.windowStart, stop.windowEnd, this.ctx.zone())}${stop.windowStrict ? ', strict' : ''}`,
      ],
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
      detail: [
        order.address.normalizedAddress || order.address.rawAddress,
        view ? 'Not on a route' : `Window ${formatWindow(order.windowStart, order.windowEnd, this.ctx.zone())}${order.windowStrict ? ', strict' : ''}`,
      ],
      latitude: order.address.latitude,
      longitude: order.address.longitude,
    }));
  });

  // One van per route that has reported, in its route's color
  protected readonly mapVehicles = computed((): MapVehicle[] => {
    const positions = this.positions();
    return (this.view()?.routes ?? []).flatMap((route) => {
      const position = positions.get(route.routeId);
      return position ? [{
        id: route.shiftId,               // same id as the route in mapRoutes, so highlighting a route dims the other vans
        fill: route.fill,
        ink: route.ink,
        title: route.vehicleName,
        detail: `Last seen ${position.recordedAt.slice(11, 16)}`,   // already on the company's clock
        latitude: position.latitude,
        longitude: position.longitude,
      }] : [];
    });
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

    // Each company and date has its own plans and its own selection
    effect(() => {
      this.ctx.id();
      this.ctx.date();
      this.selection.set(this.pendingPlanId ?? undefined);
      this.pendingPlanId = null;
      this.changedSinceSolve.set(false);
      this.planSettings.set(readPlanSettings(this.ctx.id()));
      this.excludedOrders.set(new Set());
      this.excludedShifts.set(new Set());
      this.solveError.set(null);
      this.highlighted.set(null);
      this.confirmPublish.set(false);
      this.publishError.set(null);
    });

    // Live positions for a published plan: one STOMP subscription per route. A route that hasn't started
    // simply gets no messages yet, and this way the van shows up the moment its driver presses Start.
    // Re-runs when the plan changes (another date, re-publish); onCleanup sends UNSUBSCRIBE for the old ones
    // first, and also runs when the page is left.
    effect((onCleanup) => {
      this.livePositions.set(new Map());
      const plan = this.published() ? this.saved()!.plan : null;
      if (!plan) return;
      const subscriptions = plan.routes.map((route) => this.live.routePositions(route.routeId).subscribe((position) =>
        this.livePositions.update((current) => new Map(current).set(position.routeId, position))));
      onCleanup(() => subscriptions.forEach((subscription) => subscription.unsubscribe()));
    });
  }

  protected solve(): void {
    if (!this.canSolve()) return;
    this.solving.set(true);
    this.solveError.set(null);
    this.elapsed.set(0);
    this.timer = setInterval(() => this.elapsed.update((seconds) => seconds + 1), 1000);

    const stop = () => {
      clearInterval(this.timer);
      this.solving.set(false);
    };

    this.publishError.set(null);
    const shifts = this.selectedShifts();
    this.api.solvePlan(this.ctx.id(), {
      planDate: this.ctx.date(),
      orderIds: this.selectedOrders().map((order) => order.id),
      shiftIds: shifts.map((shift) => shift.id),
      // canSolve() already waited for data, so value() is safe here
      ...toSolveRequest(this.planSettings(), shifts, this.data.value()!.vehicles),
    }).subscribe({
      next: (plan) => {
        stop();
        this.keepFresh(plan);
        this.selection.set(plan.planId);
        this.changedSinceSolve.set(false);
        this.dayPlans.reload();     // the new draft joins this day's plans
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
    this.publishing.set(true);
    this.publishError.set(null);
    // The API accepts notifyDrivers but doesn't act on it yet (no notification channel); drivers check My routes
    this.api.publishPlan(saved.plan.planId, { notifyDrivers: true }).subscribe({
      next: (plan) => {
        this.publishing.set(false);
        this.confirmPublish.set(false);
        // The publish response leaves unassignedOrderIds empty, so keep the list from solving
        this.keepFresh({ ...plan, unassignedOrderIds: saved.plan.unassignedOrderIds });
        this.dayPlans.reload();     // for the new status and publish time
      },
      error: (error: unknown) => {
        this.publishing.set(false);
        this.publishError.set(publishErrorMessage(error));
      },
    });
  }

  // Clears the screen to solve another plan. The one shown stays saved, in this day's plans and the history
  protected newPlan(): void {
    this.selection.set('new');
    this.changedSinceSolve.set(false);
    this.highlighted.set(null);
  }

  protected pickPlan(value: string): void {
    if (value === 'new') {
      this.newPlan();
      return;
    }
    this.selection.set(Number(value));
    this.changedSinceSolve.set(false);
    this.highlighted.set(null);
  }

  // "#12 · Published · 2 routes, 9 stops · 09:14"
  protected planOptionLabel(plan: PlanSummary): string {
    const status = { draft: 'Draft', published: 'Published', completed: 'Completed', solving: 'Solving', failed: 'Failed' }[plan.status];
    const at = formatTime(companyLocalToInstant(plan.publishedAt ?? plan.solvedAt, this.ctx.zone()), this.ctx.zone());
    return `#${plan.planId} · ${status} · ${plan.routeCount} ${plan.routeCount === 1 ? 'route' : 'routes'}, ${plan.stopCount} ${plan.stopCount === 1 ? 'stop' : 'stops'} · ${at}`;
  }

  private keepFresh(plan: Plan): void {
    this.freshPlans.update((current) => new Map(current).set(plan.planId, plan));
  }

  // Template helpers
  protected time(instant: string | null): string { return formatTime(instant, this.ctx.zone()); }
  protected orderWindow(start: string | null, end: string | null): string { return formatWindow(start, end, this.ctx.zone()); }
  protected overloaded(route: RouteView): boolean { return (route.loadShare ?? 0) > 1; }
  protected meterWidth(route: RouteView): string { return `${Math.min(route.loadShare ?? 0, 1) * 100}%`; }
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
