import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, forkJoin, map, of } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { ORDER_STATUSES, OrderStatus } from '../../core/models';
import { formatDay, formatTime } from '../../core/time';
import { ORDER_STATUS_LABEL } from '../../shared/badges';
import { DateNav } from '../../shared/date-nav';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';

const STATUS_COLOR: Record<OrderStatus, string> = {
  pending: '#e89d00', planned: '#2a78d6', out_for_delivery: '#6250d6',
  delivered: '#00593f', failed: '#b3261e', cancelled: '#a9b4af',
};

// Asking for one row is enough to learn a count: totalElements is the number we want
function count(request: Observable<{ totalElements: number }>): Observable<number> {
  return request.pipe(map((page) => page.totalElements));
}

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DateNav, Icon, LoadState],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);

  protected readonly dayLabel = computed(() => formatDay(this.ctx.date()));

  protected readonly data = rxResource({
    params: () => ({ id: this.ctx.id(), date: this.ctx.date(), owner: this.ctx.isOwner() }),
    stream: ({ params: { id, date, owner } }) => forkJoin({
      shifts: fetchAll((page) => this.api.shifts(id, { date, page, size: MAX_PAGE_SIZE })),
      vehicles: count(this.api.vehicles(id, { active: true, size: 1 })),
      depots: count(this.api.depots(id, { size: 1 })),
      items: count(this.api.items(id, { size: 1 })),
      // Orders and customers are owner-only endpoints
      customers: owner ? count(this.api.customers(id, { size: 1 })) : of(0),
      orders: owner ? fetchAll((page) => this.api.orders(id, { date, page, size: MAX_PAGE_SIZE })) : of([]),
    }),
  });

  protected readonly pendingCount = computed(() =>
    this.data.hasValue() ? this.data.value().orders.filter((order) => order.status === 'pending').length : 0);

  // Shares of the day's orders, for the segmented bar and its legend
  protected readonly split = computed(() => {
    const orders = this.data.hasValue() ? this.data.value().orders : [];
    return ORDER_STATUSES
      .map((status) => ({
        status, label: ORDER_STATUS_LABEL[status], color: STATUS_COLOR[status],
        count: orders.filter((order) => order.status === status).length,
      }))
      .filter((part) => part.count > 0);
  });

  // Setup order follows the API's dependencies: a vehicle needs a depot, a shift needs a vehicle, an order needs a customer and an item
  protected readonly setup = computed(() => {
    if (!this.data.hasValue()) return [];
    const data = this.data.value();
    return [
      { done: data.depots > 0, label: 'Add a depot', detail: 'Where routes start and finish', path: 'fleet' },
      { done: data.vehicles > 0, label: 'Add a vehicle', detail: 'With its weight limit', path: 'fleet' },
      { done: data.items > 0, label: 'Add the items you deliver', detail: 'Order weights are worked out from these', path: 'items' },
      { done: data.customers > 0, label: 'Add a customer', detail: 'With a default delivery address', path: 'customers' },
      { done: data.orders.length > 0, label: 'Create an order for this day', detail: 'With a delivery window', path: 'orders' },
      { done: data.shifts.length > 0, label: 'Add a shift for this day', detail: 'Puts a vehicle on the road', path: 'shifts' },
    ];
  });
  protected readonly setupComplete = computed(() => this.setup().every((step) => step.done));

  protected time(instant: string): string { return formatTime(instant, this.ctx.zone()); }
}
