import { Component, computed, input } from '@angular/core';
import { OrderStatus } from '../core/models';

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  pending: 'Pending', planned: 'Planned', out_for_delivery: 'Out for delivery',
  delivered: 'Delivered', failed: 'Failed', cancelled: 'Cancelled',
};

const STATUS_CLASS: Record<OrderStatus, string> = {
  pending: 'badge-warn', planned: 'badge-info', out_for_delivery: 'badge-info',
  delivered: 'badge-ok', failed: 'badge-danger', cancelled: '',
};

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="cssClass()">{{ label() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<OrderStatus>();
  protected readonly label = computed(() => ORDER_STATUS_LABEL[this.status()]);
  protected readonly cssClass = computed(() => STATUS_CLASS[this.status()]);
}
