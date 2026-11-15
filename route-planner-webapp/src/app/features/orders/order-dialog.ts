import { DecimalPipe } from '@angular/common';
import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, map, startWith, switchMap } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { Order, OrderRequest } from '../../core/models';
import { instantToLocal, localToInstant } from '../../core/time';
import { AddressChoice, AddressPicker, resolveAddressId } from '../../shared/address-picker';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { Modal } from '../../shared/modal';

// The new / edit order form, shared by the Orders and Plan pages. target: null keeps it closed,
// 'new' opens an empty form with a window on the selected day, an order opens it for editing.
@Component({
  selector: 'app-order-dialog',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, AddressPicker, Field, Icon, Modal],
  styles: `
    .line { display: grid; grid-template-columns: minmax(0, 1fr) 96px auto; gap: 8px; align-items: start; margin-bottom: 8px; }
    .lines-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
  `,
  template: `
    <app-modal [open]="target() !== null" [wide]="true" [heading]="editing() ? 'Edit order #' + editing()?.id : 'New order'" (closed)="closed.emit()">
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (runner.error()) { <div class="banner banner-error" role="alert" style="margin-bottom:12px">{{ runner.error() }}</div> }

        @if (lookups.hasValue() && (customers().length === 0 || items().length === 0)) {
          <div class="banner banner-info" style="margin-bottom:12px">
            Orders need a customer and at least one item.
            <a [routerLink]="ctx.link(customers().length === 0 ? 'customers' : 'items')">Add {{ customers().length === 0 ? 'a customer' : 'an item' }} first</a>.
          </div>
        }

        <div class="form-grid">
          <app-field class="span-2" label="Customer" [control]="form.controls.customerId">
            <select class="input" formControlName="customerId">
              <option [ngValue]="null" disabled>Choose a customer</option>
              @for (customer of customers(); track customer.id) { <option [ngValue]="customer.id">{{ customer.name }}</option> }
            </select>
          </app-field>

          <div class="field span-2">
            <span class="field-label">Deliver to</span>
            @if (!address()) {
              @if (editing(); as order) {
                <p class="small muted">Now: {{ order.address.normalizedAddress || order.address.rawAddress }}. Search below to change it.</p>
              } @else if (selectedCustomer()?.defaultAddress; as def) {
                <p class="small muted">Customer's default: {{ def.normalizedAddress || def.rawAddress }}. Search below only to send this order elsewhere.</p>
              }
            }
            <app-address-picker [choice]="address()" (choiceChange)="address.set($event)" />
          </div>

          <div class="field span-2" formArrayName="lines">
            <div class="lines-head">
              <span class="field-label">Items</span>
              <span class="small muted">About {{ estimatedWeight() | number: '1.0-1' }} kg in total</span>
            </div>
            @for (line of lines.controls; track line; let i = $index) {
              <div class="line" [formGroupName]="i">
                <app-field label="Item" [control]="line.controls.itemId">
                  <select class="input" formControlName="itemId">
                    <option [ngValue]="null" disabled>Choose an item</option>
                    @for (item of items(); track item.id) { <option [ngValue]="item.id">{{ item.name }} ({{ item.weightKg }} kg)</option> }
                  </select>
                </app-field>
                <app-field label="Quantity" [control]="line.controls.quantity"><input class="input" type="number" min="1" max="10000" formControlName="quantity" /></app-field>
                <button type="button" class="btn btn-quiet btn-icon" style="margin-top:23px" aria-label="Remove line" [disabled]="lines.length === 1" (click)="lines.removeAt(i)"><app-icon name="trash" /></button>
              </div>
            }
            <div><button type="button" class="btn btn-small" (click)="lines.push(newLine())"><app-icon name="plus" /> Add item</button></div>
          </div>

          <app-field label="Window opens" [optional]="true" [hint]="'In ' + ctx.zone()" [control]="form.controls.windowStart"><input class="input" type="datetime-local" formControlName="windowStart" /></app-field>
          <app-field label="Window closes" [optional]="true" [control]="form.controls.windowEnd"><input class="input" type="datetime-local" formControlName="windowEnd" /></app-field>

          <label class="check span-2">
            <input type="checkbox" formControlName="windowStrict" />
            <span>Strict window<span class="cell-sub">The planner won't plan a late arrival. Otherwise lateness is allowed but costly.</span></span>
          </label>

          <app-field label="Minutes at the stop" [control]="form.controls.serviceMinutes"><input class="input" type="number" min="0" max="480" formControlName="serviceMinutes" /></app-field>
          <app-field class="span-2" label="Notes" [optional]="true" [control]="form.controls.notes"><textarea class="input" formControlName="notes" placeholder="Gate code, loading dock, who to ask for"></textarea></app-field>
        </div>

        <div class="form-actions">
          <button type="button" class="btn" (click)="closed.emit()">Cancel</button>
          <button class="btn btn-primary" [disabled]="runner.busy()">{{ runner.busy() ? 'Saving…' : 'Save order' }}</button>
        </div>
      </form>
    </app-modal>
  `,
})
export class OrderDialog {
  private readonly api = inject(Api);
  private readonly fb = inject(FormBuilder);
  protected readonly ctx = inject(CompanyContext);

  readonly target = input<Order | 'new' | null>(null);
  readonly saved = output<Order>();
  readonly closed = output<void>();

  protected readonly editing = computed(() => {
    const target = this.target();
    return target === 'new' ? null : target;
  });

  // Dropdown data, loaded the first time the form opens rather than with the page
  protected readonly lookups = rxResource({
    params: () => (this.target() !== null ? { id: this.ctx.id() } : undefined),
    stream: ({ params: { id } }) => forkJoin({
      customers: fetchAll((page) => this.api.customers(id, { page, size: MAX_PAGE_SIZE })),
      items: fetchAll((page) => this.api.items(id, { page, size: MAX_PAGE_SIZE })),
    }),
  });
  protected readonly customers = computed(() => (this.lookups.hasValue() ? this.lookups.value().customers : []));
  protected readonly items = computed(() => (this.lookups.hasValue() ? this.lookups.value().items : []));

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

  constructor() {
    // Fill the form each time it opens. untracked: typing must not re-run this
    effect(() => {
      const target = this.target();
      if (target !== null) untracked(() => this.reset(target));
    });
  }

  protected newLine(itemId: number | null = null, quantity = 1) {
    return this.fb.group({
      itemId: [itemId, Validators.required],
      quantity: [quantity, [Validators.required, Validators.min(1), Validators.max(10000)]],
    });
  }

  private reset(target: Order | 'new'): void {
    const order = target === 'new' ? null : target;
    this.address.set(null);
    this.runner.reset();
    // reset() sets every row of the FormArray too, so the rows have to exist first and their values go through it
    const lines = order?.items.map((line) => ({ itemId: line.itemId as number | null, quantity: line.quantity })) ?? [{ itemId: null, quantity: 1 }];
    this.lines.clear();
    lines.forEach(() => this.lines.push(this.newLine()));
    const zone = this.ctx.zone();
    const date = this.ctx.date();
    this.form.reset({
      customerId: order?.customer.id ?? null,
      lines,
      // A new order starts with a 09:00–17:00 window on the day being viewed
      windowStart: order ? (order.windowStart ? instantToLocal(order.windowStart, zone) : '') : `${date}T09:00`,
      windowEnd: order ? (order.windowEnd ? instantToLocal(order.windowEnd, zone) : '') : `${date}T17:00`,
      windowStrict: order?.windowStrict ?? false,
      serviceMinutes: order?.serviceMinutes ?? 5,
      notes: order?.notes ?? '',
    });
    if (order) this.form.controls.customerId.disable(); else this.form.controls.customerId.enable();
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

    this.runner.run(request, (order) => this.saved.emit(order), this.form);
  }
}
