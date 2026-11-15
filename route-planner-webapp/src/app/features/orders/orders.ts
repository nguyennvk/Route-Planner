import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { ORDER_STATUSES, Order, OrderStatus } from '../../core/models';
import { formatDateTime, formatWindow } from '../../core/time';
import { ORDER_STATUS_LABEL, StatusBadge } from '../../shared/badges';
import { Confirm } from '../../shared/confirm';
import { DateNav } from '../../shared/date-nav';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { Pager } from '../../shared/pager';
import { OrderDialog } from './order-dialog';

@Component({
  selector: 'app-orders',
  imports: [DecimalPipe, Confirm, DateNav, Icon, LoadState, OrderDialog, Pager, StatusBadge],
  templateUrl: './orders.html',
  styles: `
    .wide { min-width: 900px; }
    .window { white-space: nowrap; }
    .all-dates { align-self: center; }
    @media (max-width: 720px) { .toolbar .input { flex: 1 1 140px; } }
  `,
})
export class Orders {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);

  protected readonly statuses = ORDER_STATUSES;
  protected readonly statusLabel = ORDER_STATUS_LABEL;

  // Filters
  // Orders with no delivery window have no date, so the API only returns them when no date is sent
  protected readonly allDates = signal(false);
  protected readonly status = signal<OrderStatus | null>(null);
  protected readonly customerFilter = signal<number | null>(null);
  protected readonly page = signal(0);

  protected readonly orders = rxResource({
    params: () => ({
      id: this.ctx.id(), date: this.allDates() ? null : this.ctx.date(),
      status: this.status(), customerId: this.customerFilter(), page: this.page(),
    }),
    stream: ({ params }) => this.api.orders(params.id, { date: params.date, status: params.status, customerId: params.customerId, page: params.page, size: 50 }),
  });

  // Customers for the filter; the order form loads its own
  private readonly lookups = rxResource({
    params: () => this.ctx.id(),
    stream: ({ params: id }) => fetchAll((page) => this.api.customers(id, { page, size: MAX_PAGE_SIZE })),
  });
  protected readonly customers = computed(() => (this.lookups.hasValue() ? this.lookups.value() : []));

  protected window(order: Order): string { return formatWindow(order.windowStart, order.windowEnd, this.ctx.zone()); }
  protected day(order: Order): string { return order.windowStart ? formatDateTime(order.windowStart, this.ctx.zone()).split(',')[0] : ''; }

  // New / edit: the shared dialog. null keeps it closed
  protected readonly dialogTarget = signal<Order | 'new' | null>(null);

  protected orderSaved(): void {
    this.dialogTarget.set(null);
    this.orders.reload();
  }

  // Cancel
  protected readonly cancelling = signal<Order | null>(null);
  protected readonly cancelRunner = new FormRunner();
  protected readonly cancelMessage = computed(() => {
    const order = this.cancelling();
    return order ? `Order #${order.id} for ${order.customer.name} stays on record as cancelled and won't be planned.` : '';
  });

  protected openCancel(order: Order): void {
    this.cancelRunner.reset();
    this.cancelling.set(order);
  }

  protected confirmCancel(): void {
    const order = this.cancelling();
    if (!order) return;
    this.cancelRunner.run(this.api.cancelOrder(order.id), () => {
      this.cancelling.set(null);
      this.orders.reload();
    });
  }

  // The customer's tracking page (/track/{token}, no login). The owner sends it on; there's no email or SMS yet
  protected readonly copied = signal<number | null>(null);
  private copiedTimer: ReturnType<typeof setTimeout> | undefined;

  protected copyTrackingLink(order: Order): void {
    const link = `${location.origin}/track/${order.trackingToken}`;
    navigator.clipboard.writeText(link).then(
      () => {
        this.copied.set(order.id);
        clearTimeout(this.copiedTimer);
        this.copiedTimer = setTimeout(() => this.copied.set(null), 2500);
      },
      // Clipboard access can be refused (permissions, non-HTTPS host); open the page instead so the link can be copied from there
      () => window.open(link, '_blank', 'noopener'),
    );
  }

}
