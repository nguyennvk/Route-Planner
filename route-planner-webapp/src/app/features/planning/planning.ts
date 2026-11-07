import { DecimalPipe, PercentPipe } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { forkJoin, map } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { errorMessage } from '../../core/errors';
import { Plan } from '../../core/models';
import { dateInZone, formatDateTime, formatTime, formatWindow } from '../../core/time';
import { DateNav } from '../../shared/date-nav';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { MapDepot, MapRoute, MapStop, RouteMap } from '../../shared/map/route-map';
import { RouteView, buildPlanView, softCostLabel } from './plan-view';

interface SavedPlan { plan: Plan; solvedAt: string; }

@Component({
  selector: 'app-planning',
  imports: [DecimalPipe, PercentPipe, RouterLink, DateNav, Icon, LoadState, RouteMap],
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

  protected readonly candidates = computed(() => (this.data.hasValue() ? this.data.value().candidates : []));
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

  // The API doesn't store plans and has no endpoint to read one back, so this browser is the only copy
  private readonly storageKey = computed(() => `route-scheduler.plan.${this.ctx.id()}.${this.ctx.date()}`);

  protected readonly canSolve = computed(() => !this.solving() && this.selectedOrders().length > 0 && this.selectedShifts().length > 0);

  protected readonly view = computed(() => {
    const saved = this.saved();
    if (!saved || !this.data.hasValue()) return null;
    const { orders, shifts, vehicles, depots } = this.data.value();
    return buildPlanView(saved.plan, { orders, shifts, vehicles, depots });
  });
  protected readonly solvedAtLabel = computed(() => {
    const saved = this.saved();
    return saved ? formatDateTime(saved.solvedAt, this.ctx.zone()) : '';
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

function readSaved(key: string): SavedPlan | null {
  try {
    return JSON.parse(localStorage.getItem(key) ?? 'null');
  } catch {
    return null;
  }
}
