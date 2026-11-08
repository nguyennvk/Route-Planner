import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { formatDay, formatTime, shiftDate, todayIn } from '../../core/time';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { MapDepot, MapRoute, RouteMap } from '../../shared/map/route-map';
import { ROUTE_COLORS } from '../planning/plan-view';
import { DriverRouteView, buildDriverRoutes } from './my-routes-view';

@Component({
  selector: 'app-my-routes',
  imports: [NgTemplateOutlet, RouterLink, Icon, LoadState, RouteMap],
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

  // The route in the URL, else the next one to drive, else the latest one driven
  protected readonly selected = computed((): DriverRouteView | null => {
    const id = Number(this.route());
    return this.routes().find((route) => route.routeId === id) ?? this.upcoming()[0] ?? this.earlier()[0] ?? null;
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

  protected dayLabel(date: string | null): string {
    if (!date) return 'No stops';
    if (date === this.today()) return 'Today';
    if (date === shiftDate(this.today(), 1)) return 'Tomorrow';
    return formatDay(date);
  }

  protected time(instant: string | null): string { return formatTime(instant, this.ctx.zone()); }
}
