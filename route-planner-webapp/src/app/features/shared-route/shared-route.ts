import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Api } from '../../core/api';
import { errorMessage } from '../../core/errors';
import { PlanRoute } from '../../core/models';
import { formatDay } from '../../core/time';
import { Brand } from '../../shared/brand';
import { Icon } from '../../shared/icon';
import { MapRoute, RouteMap } from '../../shared/map/route-map';
import { RouteActions, actionErrorMessage } from '../../shared/route-actions';
import { ROUTE_COLORS, decodePath } from '../planning/plan-view';

// /route/{token}: the driver's link that opens without logging in, e.g. from a text message.
// Without a login it can't read the company's vehicles or addresses, so it shows what the route itself carries:
// raw addresses, wall-clock times and the road path.
@Component({
  selector: 'app-shared-route',
  imports: [RouterLink, Brand, Icon, RouteActions, RouteMap],
  templateUrl: './shared-route.html',
  styleUrl: './shared-route.css',
})
export class SharedRoute {
  private readonly api = inject(Api);

  // The :token path segment, through withComponentInputBinding() like @PathVariable
  readonly token = input.required<string>();

  protected readonly data = rxResource({
    params: () => this.token(),
    stream: ({ params: token }) => this.api.sharedRoute(token),
  });

  protected readonly notFound = computed(() => {
    const error = this.data.error();
    const cause = error instanceof Error && error.cause ? error.cause : error;
    return cause instanceof HttpErrorResponse && cause.status === 404;
  });

  protected readonly stops = computed(() =>
    this.data.hasValue() ? [...this.data.value().stops].sort((a, b) => a.sequence - b.sequence) : []);

  // Times arrive on the company's clock with no zone ("2026-11-03T08:06:00"), so they're shown as written
  protected readonly date = computed(() => this.stops()[0]?.arrivalTime?.slice(0, 10) ?? null);

  protected readonly mapRoutes = computed((): MapRoute[] => {
    const geometry = this.data.hasValue() ? this.data.value().geometry : null;
    if (!geometry) return [];
    const color = ROUTE_COLORS[0];
    return [{ id: this.data.value()!.routeId, fill: color.fill, ink: color.ink, start: null, stops: [], path: decodePath(geometry) }];
  });

  protected readonly acting = signal(false);
  protected readonly actionError = signal<string | null>(null);

  protected accept(): void { this.act(this.api.acceptSharedRoute(this.token())); }
  protected reject(reason: string | null): void { this.act(this.api.rejectSharedRoute(this.token(), reason)); }
  protected start(): void { this.act(this.api.startSharedRoute(this.token())); }
  protected finish(): void { this.act(this.api.finishSharedRoute(this.token())); }

  private act(request: Observable<PlanRoute>): void {
    if (this.acting()) return;
    this.acting.set(true);
    this.actionError.set(null);
    request.subscribe({
      next: (route) => {
        this.acting.set(false);
        this.data.value.set(route);
      },
      error: (error: unknown) => {
        this.acting.set(false);
        const status = error instanceof HttpErrorResponse ? error.status : undefined;
        this.actionError.set(actionErrorMessage(status, errorMessage(error)));
      },
    });
  }

  protected readonly errorText = computed(() => errorMessage(this.data.error()));

  protected day(date: string | null): string { return date ? formatDay(date) : 'No stops'; }
  protected time(local: string | null): string { return local ? local.slice(11, 16) : '–'; }
}
