import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, map, startWith, switchMap } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { ORDER_STATUSES, Order, OrderRequest, OrderStatus } from '../../core/models';
import { formatDateTime, formatWindow, instantToLocal, localToInstant } from '../../core/time';
import { AddressChoice, AddressPicker, resolveAddressId } from '../../shared/address-picker';
import { ORDER_STATUS_LABEL, StatusBadge } from '../../shared/badges';
import { Confirm } from '../../shared/confirm';
import { DateNav } from '../../shared/date-nav';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { Modal } from '../../shared/modal';
import { Pager } from '../../shared/pager';

@Component({
  selector: 'app-orders',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, AddressPicker, Confirm, DateNav, Field, Icon, LoadState, Modal, Pager, StatusBadge],
  templateUrl: './orders.html',
  styles: `
    .line { display: grid; grid-template-columns: minmax(0, 1fr) 96px auto; gap: 8px; align-items: start; margin-bottom: 8px; }
    .lines-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
    .wide { min-width: 900px; }
    .window { white-space: nowrap; }
    .all-dates { align-self: center; }
    @media (max-width: 720px) { .toolbar .input { flex: 1 1 140px; } }
  `,
})
export class Orders {
  private readonly api = inject(Api);
  private readonly fb = inject(FormBuilder);
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

  // Dropdown data for the filter and the form
  protected readonly lookups = rxResource({
    params: () => this.ctx.id(),
    stream: ({ params: id }) => forkJoin({
      customers: fetchAll((page) => this.api.customers(id, { page, size: MAX_PAGE_SIZE })),
      items: fetchAll((page) => this.api.items(id, { page, size: MAX_PAGE_SIZE })),
    }),
  });
  protected readonly customers = computed(() => (this.lookups.hasValue() ? this.lookups.value().customers : []));
  protected readonly items = computed(() => (this.lookups.hasValue() ? this.lookups.value().items : []));

  protected window(order: Order): string { return formatWindow(order.windowStart, order.windowEnd, this.ctx.zone()); }
  protected day(order: Order): string { return order.windowStart ? formatDateTime(order.windowStart, this.ctx.zone()).split(',')[0] : ''; }

  // Form
  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<Order | null>(null);
  protected readonly address = signal<AddressChoice | null>(null);
  protected readonly runner = new FormRunner();
  protected readonly lines = this.fb.array([this.newLine()]);
  protected readonly form = this.fb.group({
    customerId: [null as number | null, Validators.required],
    lines: this.lines,
    windowStart: [''],
    windowEnd: [''],
    windowStrict: [false],
    serviceMinutes: [5, [Validators.required, Validators.min(0), Validators.max(480)]],
    notes: ['', Validators.maxLength(2000)],
  });

  private readonly formValue = toSignal(this.form.valueChanges.pipe(startWith(null), map(() => this.form.getRawValue())), { requireSync: true });

  protected readonly selectedCustomer = computed(() => this.customers().find((c) => c.id === Number(this.formValue().customerId)) ?? null);

  protected readonly estimatedWeight = computed(() => {
    const items = new Map(this.items().map((item) => [item.id, item.weightKg]));
    return this.formValue().lines.reduce((sum, line) => sum + (items.get(Number(line.itemId)) ?? 0) * (line.quantity ?? 0), 0);
  });

  private newLine(itemId: number | null = null, quantity = 1) {
    return this.fb.group({
      itemId: [itemId, Validators.required],
      quantity: [quantity, [Validators.required, Validators.min(1), Validators.max(10000)]],
    });
  }

  protected addLine(): void { this.lines.push(this.newLine()); }
  protected removeLine(index: number): void { this.lines.removeAt(index); }

  protected openNew(): void {
    this.open(null);
    const date = this.ctx.date();
    this.form.patchValue({ windowStart: `${date}T09:00`, windowEnd: `${date}T17:00` });
  }

  protected openEdit(order: Order): void { this.open(order); }

  private open(order: Order | null): void {
    this.editing.set(order);
    this.address.set(null);
    this.runner.reset();
    // reset() sets every row of the FormArray too, so the rows have to exist first and their values go through it
    const lines = order?.items.map((line) => ({ itemId: line.itemId as number | null, quantity: line.quantity })) ?? [{ itemId: null, quantity: 1 }];
    this.lines.clear();
    lines.forEach(() => this.lines.push(this.newLine()));
    const zone = this.ctx.zone();
    this.form.reset({
      customerId: order?.customer.id ?? null,
      lines,
      windowStart: order?.windowStart ? instantToLocal(order.windowStart, zone) : '',
      windowEnd: order?.windowEnd ? instantToLocal(order.windowEnd, zone) : '',
      windowStrict: order?.windowStrict ?? false,
      serviceMinutes: order?.serviceMinutes ?? 5,
      notes: order?.notes ?? '',
    });
    if (order) this.form.controls.customerId.disable(); else this.form.controls.customerId.enable();
    this.dialogOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const zone = this.ctx.zone();
    const start = value.windowStart ? localToInstant(value.windowStart, zone) : undefined;
    const end = value.windowEnd ? localToInstant(value.windowEnd, zone) : undefined;
    if (start && end && end <= start) {
      this.runner.error.set('The delivery window must end after it starts.');
      return;
    }
    const editing = this.editing();
    const customer = this.selectedCustomer();
    if (!editing && !this.address() && !customer?.defaultAddress) {
      this.runner.error.set('This customer has no default address, so choose where to deliver.');
      return;
    }

    const body: OrderRequest = {
      items: value.lines.map((line) => ({ itemId: Number(line.itemId), quantity: Number(line.quantity) })),
      windowStart: start, windowEnd: end, windowStrict: !!value.windowStrict,
      serviceMinutes: Number(value.serviceMinutes), notes: value.notes || undefined,
    };
    const request = resolveAddressId(this.api, this.ctx.id(), this.address()).pipe(switchMap((addressId) => editing
      ? this.api.updateOrder(editing.id, { ...body, addressId })
      : this.api.createOrder(this.ctx.id(), { ...body, addressId, customerId: Number(value.customerId) })));

    this.runner.run(request, () => {
      this.dialogOpen.set(false);
      this.orders.reload();
    }, this.form);
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

  protected lineControls() { return this.lines.controls; }
}
