import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, exhaustMap, takeWhile, timer } from 'rxjs';
import { Api } from '../../core/api';
import { errorMessage } from '../../core/errors';
import { Tracking, TrackingStage } from '../../core/models';
import { formatDay } from '../../core/time';
import { Brand } from '../../shared/brand';
import { Icon } from '../../shared/icon';
import { MapRoute, MapStop, MapVehicle, RouteMap } from '../../shared/map/route-map';
import { ROUTE_COLORS, decodePath } from '../planning/plan-view';

const REFRESH_MS = 20_000;
// Below this, "late" is normal day-to-day drift and apologising would be noise
const LATE_NOTICE_MINUTES = 15;
const FINAL: TrackingStage[] = ['delivered', 'failed'];

// /track/{token}: the customer's link, no login. Shows only their own order: the van appears once it's coming
// to them (the API leaves it out before that, since it would be standing at another customer's door).
@Component({
  selector: 'app-track-order',
  imports: [RouterLink, Brand, Icon, RouteMap],
  templateUrl: './track-order.html',
  styleUrl: './track-order.css',
})
export class TrackOrder {
  private readonly api = inject(Api);

  // The :token path segment, through withComponentInputBinding()
  readonly token = input.required<string>();

  // Polls every 20 s until the order is delivered or failed. A failed refresh keeps the last answer on screen
  // (a phone on a weak signal shouldn't swap the page for an error); only the first load can fail the page.
  protected readonly data = rxResource({
    params: () => this.token(),
    stream: ({ params: token }) => timer(0, REFRESH_MS).pipe(
      exhaustMap((tick) => tick === 0 ? this.api.trackOrder(token) : this.api.trackOrder(token).pipe(catchError(() => EMPTY))),
      takeWhile((tracking) => !FINAL.includes(tracking.stage), true),   // true: still emit the final answer
    ),
  });

  protected readonly notFound = computed(() => {
    const error = this.data.error();
    const cause = error instanceof Error && error.cause ? error.cause : error;
    return cause instanceof HttpErrorResponse && cause.status === 404;
  });
  protected readonly errorText = computed(() => errorMessage(this.data.error()));
  protected readonly live = computed(() => this.data.hasValue() && !FINAL.includes(this.data.value().stage));

  protected readonly headline = computed(() => {
    if (!this.data.hasValue()) return '';
    const t = this.data.value();
    switch (t.stage) {
      case 'scheduled': return this.isToday(t.planDate) ? 'Your order is out for delivery today' : `Your order is scheduled for ${formatDay(t.planDate)}`;
      case 'on_the_way': return 'Your order is on its way';
      case 'next': return "You're next!";
      case 'arrived': return 'The driver is at your door';
      case 'delivered': return 'Your order was delivered';
      case 'failed': return "We couldn't deliver your order";
    }
  });

  // The apology: shown while the delivery is still to come and it's running noticeably behind plan
  protected readonly lateMessage = computed(() => {
    if (!this.data.hasValue()) return null;
    const t = this.data.value();
    if (!['scheduled', 'on_the_way', 'next'].includes(t.stage)) return null;
    const pastWindow = !!t.windowEnd && !!t.etaStart && t.etaStart > t.windowEnd;   // same-format wall-clock strings
    if (t.delayMinutes < LATE_NOTICE_MINUTES && !pastWindow) return null;
    // Past a couple of hours an exact figure reads as a system glitch rather than an apology
    const by = t.delayMinutes >= 120 ? ' by more than 2 hours'
      : t.delayMinutes >= LATE_NOTICE_MINUTES ? ` by about ${duration(t.delayMinutes)}` : '';
    const window = pastWindow ? ` That's later than the ${clock(t.windowStart)}–${clock(t.windowEnd)} window you chose.` : '';
    return `Sorry, your delivery is running late${by}.${window} We're on our way and will be with you as soon as we can.`;
  });

  protected readonly mapRoutes = computed((): MapRoute[] => {
    const t = this.data.hasValue() ? this.data.value() : null;
    if (!t?.geometry || t.latitude === null || t.longitude === null) return [];
    const color = ROUTE_COLORS[0];
    return [{ id: 1, fill: color.fill, ink: color.ink, start: null, stops: [this.pin(t)!], path: decodePath(t.geometry) }];
  });

  // Without a road path the pin is drawn on its own
  protected readonly mapLoose = computed((): MapStop[] => {
    const t = this.data.hasValue() ? this.data.value() : null;
    const pin = t && !t.geometry ? this.pin(t) : null;
    return pin ? [pin] : [];
  });

  protected readonly mapVehicles = computed((): MapVehicle[] => {
    const t = this.data.hasValue() ? this.data.value() : null;
    if (!t || t.vehicleLatitude === null || t.vehicleLongitude === null) return [];
    const color = ROUTE_COLORS[0];
    return [{ id: 1, fill: color.fill, ink: color.ink, title: 'Your delivery', detail: `Updated ${clock(t.vehicleRecordedAt)}`,
      latitude: t.vehicleLatitude, longitude: t.vehicleLongitude }];
  });

  protected readonly showMap = computed(() => this.mapRoutes().length > 0 || this.mapLoose().length > 0);

  private pin(t: Tracking): MapStop | null {
    if (t.latitude === null || t.longitude === null) return null;
    return { label: '', title: 'Your address', detail: t.address, latitude: t.latitude, longitude: t.longitude };
  }

  private isToday(date: string): boolean { return date === new Date().toLocaleDateString('en-CA'); }   // YYYY-MM-DD, local

  protected clock(local: string | null): string { return clock(local); }
}

// "2026-11-12T14:25:48" → "14:25"; the times are already on the company's clock
function clock(local: string | null): string { return local ? local.slice(11, 16) : '–'; }

function duration(minutes: number): string {
  if (minutes < 60) return `${minutes} minutes`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest ? `${hours} h ${rest} min` : `${hours} h`;
}
