import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin, retry } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { errorMessage } from '../../core/errors';
import { PlanRoute } from '../../core/models';
import { formatDay, formatTime, shiftDate, todayIn } from '../../core/time';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { MapDepot, MapRoute, MapVehicle, RouteMap } from '../../shared/map/route-map';
import { RouteActions, actionErrorMessage } from '../../shared/route-actions';
import { StopActions } from '../../shared/stop-actions';
import { ROUTE_COLORS } from '../planning/plan-view';
import { DriverRouteView, buildDriverRoutes } from './my-routes-view';

@Component({
  selector: 'app-my-routes',
  imports: [NgTemplateOutlet, RouterLink, Icon, LoadState, RouteActions, RouteMap, StopActions],
  templateUrl: './my-routes.html',
  styleUrl: './my-routes.css',
})
export class MyRoutes {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);

  // ?route=12 fills this through withComponentInputBinding(), the way @RequestParam fills a controller
  // argument. Keeping the choice in the URL means a refresh or a shared link opens the same route.
  readonly route = input<string>();

  protected readonly data = rxResource({
    params: () => this.ctx.id(),
    stream: ({ params: id }) => forkJoin({
      routes: this.api.myRoutes(),
      vehicles: fetchAll((page) => this.api.vehicles(id, { page, size: MAX_PAGE_SIZE })),
      depots: fetchAll((page) => this.api.depots(id, { page, size: MAX_PAGE_SIZE })),
      addresses: fetchAll((page) => this.api.addresses(id, { page, size: MAX_PAGE_SIZE })),
    }),
  });

  protected readonly routes = computed(() =>
    this.data.hasValue() ? buildDriverRoutes(this.data.value().routes, this.data.value(), this.ctx.zone()) : []);

  private readonly today = computed(() => todayIn(this.ctx.zone()));
  // The API sorts by planned start, so upcoming reads soonest first and earlier is flipped to latest first
  protected readonly upcoming = computed(() => this.routes().filter((route) => !route.date || route.date >= this.today()));
  protected readonly earlier = computed(() => this.routes().filter((route) => route.date && route.date < this.today()).reverse());

  // The route in the URL, else the one being driven, else the next one to drive, else the latest one driven
  protected readonly selected = computed((): DriverRouteView | null => {
    const id = Number(this.route());
    return this.routes().find((route) => route.routeId === id)
      ?? this.routes().find((route) => route.startedAt && !route.finishedAt)
      ?? this.upcoming()[0] ?? this.earlier()[0] ?? null;
  });

  protected readonly mapRoutes = computed((): MapRoute[] => {
    const route = this.selected();
    if (!route) return [];
    const color = ROUTE_COLORS[0];
    return [{
      id: route.routeId,
      fill: color.fill,
      ink: color.ink,
      start: route.depot ? [route.depot.address.longitude, route.depot.address.latitude] : null,
      stops: route.stops.filter((stop) => stop.latitude !== null).map((stop) => ({
        label: String(stop.sequence + 1),
        title: stop.address,
        detail: `Arrive ${this.time(stop.arrival)}`,
        latitude: stop.latitude!,
        longitude: stop.longitude!,
      })),
      path: route.path,
    }];
  });

  protected readonly mapDepots = computed((): MapDepot[] => {
    const depot = this.selected()?.depot;
    return depot ? [{ name: depot.name, latitude: depot.address.latitude, longitude: depot.address.longitude }] : [];
  });

  // Which route has a request in flight, so switching routes mid-request doesn't show its spinner on another one
  protected readonly acting = signal<number | null>(null);
  protected readonly actionError = signal<{ routeId: number; message: string } | null>(null);

  // GPS watch for the route being driven. It lives in the open page, so a reload loses it:
  // the effect below starts it from the route's state (started, not finished) rather than from the Start click.
  private watchId: number | null = null;
  private watchedRouteId: number | null = null;

  // The phone's own latest GPS fix. The driver doesn't need the websocket for this: it's the same fix
  // onFix sends to the API, so the van moves on their map without a round trip to the server
  private readonly myPosition = signal<{ routeId: number; latitude: number; longitude: number; at: string } | null>(null);

  // The van on the driver's map: only on the route being driven, and only once the phone has a fix
  protected readonly mapVehicles = computed((): MapVehicle[] => {
    const route = this.selected();
    const position = this.myPosition();
    if (!route || !position || position.routeId !== route.routeId) return [];
    const color = ROUTE_COLORS[0];
    return [{
      id: route.routeId,                 // same id as the route in mapRoutes
      fill: color.fill,
      ink: color.ink,
      title: `You (${route.vehicleName})`,
      detail: `Updated ${this.time(position.at)}`,
      latitude: position.latitude,
      longitude: position.longitude,
    }];
  });

  constructor() {
    // Re-runs whenever routes() changes: after load, after Start or Finish swaps the route in, after a reload
    effect(() => {
      const active = this.routes().find((route) => route.startedAt && !route.finishedAt);
      this.track(active?.routeId ?? null);
    });
    inject(DestroyRef).onDestroy(() => this.track(null));   // leaving the page stops the GPS
  }

  private track(routeId: number | null): void {
    if (routeId === this.watchedRouteId) return;            // already watching this route, or nothing to watch
    if (this.watchId !== null) navigator.geolocation.clearWatch(this.watchId);
    this.watchId = null;
    this.watchedRouteId = routeId;
    this.myPosition.set(null);                              // a fix from the previous route doesn't belong on this one
    if (routeId === null || !('geolocation' in navigator)) return;
    this.watchId = navigator.geolocation.watchPosition(
      (position) => this.onFix(position, routeId),
      (error) => this.onError(error),
      { enableHighAccuracy: true, maximumAge: 5_000, timeout: 20_000 },
    );
  }

  private onFix(position: GeolocationPosition, routeId: number): void {
    this.myPosition.set({
      routeId,
      latitude: position.coords.latitude,
      longitude: position.coords.longitude,
      at: new Date(position.timestamp).toISOString(),       // an instant, so time() shows it on the company's clock
    });
    this.api.postDriverPosition(routeId, {
      latitude: position.coords.latitude,
      longitude: position.coords.longitude,
      speedKmh: position.coords.speed === null ? null : position.coords.speed * 3.6,   // m/s → km/h
    }).pipe(
      retry({count: 3, delay: 3_000})
    ).subscribe({
      error: () => {}
    });

  }

  private onError(error: GeolocationPositionError): void {
    console.log("Reject")
  }

  protected accept(routeId: number): void { this.act(routeId, this.api.acceptMyRoute(routeId)); }
  protected reject(routeId: number, reason: string | null): void { this.act(routeId, this.api.rejectMyRoute(routeId, reason)); }
  // The effect in the constructor starts tracking once the started route comes back
  protected start(routeId: number): void { this.act(routeId, this.api.startMyRoute(routeId)); }

  protected finish(routeId: number): void { this.act(routeId, this.api.finishMyRoute(routeId)); }

  // A stop's answer comes back as the whole route, swapped in like the route actions
  protected arriveStop(routeId: number, stopId: number): void { this.act(routeId, this.api.arriveMyStop(routeId, stopId)); }
  protected deliverStop(routeId: number, stopId: number): void { this.act(routeId, this.api.deliverMyStop(routeId, stopId)); }
  protected failStop(routeId: number, stopId: number, reason: string): void { this.act(routeId, this.api.failMyStop(routeId, stopId, reason)); }

  private act(routeId: number, request: Observable<PlanRoute>): void {
    if (this.acting() !== null) return;
    this.acting.set(routeId);
    this.actionError.set(null);
    request.subscribe({
      // Swap the updated route into the loaded data instead of reloading: the map and list stay put
      next: (updated) => {
        this.acting.set(null);
        this.data.value.update((value) => value && {
          ...value,
          routes: value.routes.map((route) => (route.routeId === updated.routeId ? updated : route)),
        });
      },
      error: (error: unknown) => {
        this.acting.set(null);
        const status = error instanceof HttpErrorResponse ? error.status : undefined;
        this.actionError.set({ routeId, message: actionErrorMessage(status, errorMessage(error)) });
      },
    });
  }

  protected dayLabel(date: string | null): string {
    if (!date) return 'No stops';
    if (date === this.today()) return 'Today';
    if (date === shiftDate(this.today(), 1)) return 'Tomorrow';
    return formatDay(date);
  }

  protected time(instant: string | null): string { return formatTime(instant, this.ctx.zone()); }
}
