import { Component, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { debounceTime, distinctUntilChanged, map, startWith, switchMap, tap } from 'rxjs';
import { Api } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { Customer, CustomerRequest } from '../../core/models';
import { AddressChoice, AddressPicker, resolveAddressId } from '../../shared/address-picker';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { Modal } from '../../shared/modal';
import { Pager } from '../../shared/pager';

export const E164 = /^\+[1-9][0-9]{1,14}$/;

@Component({
  selector: 'app-customers',
  imports: [ReactiveFormsModule, AddressPicker, Field, Icon, LoadState, Modal, Pager],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Customers</h1><p>Who you deliver to. A default address saves picking one on every order.</p></div>
        <button type="button" class="btn btn-primary" (click)="openNew()"><app-icon name="plus" /> New customer</button>
      </div>

      <div class="toolbar">
        <input class="input" style="min-width:280px" [formControl]="search" type="search" placeholder="Search by name or email" aria-label="Search customers" />
      </div>

      @if (customers.hasValue()) {
        @if (customers.value().content.length === 0) {
          <div class="card empty">
            <h2>{{ term() ? 'No customers match that search' : 'No customers yet' }}</h2>
            @if (!term()) { <p>Add a customer before taking their first order.</p> }
          </div>
        } @else {
          <div class="table-wrap"><table class="data">
            <thead><tr><th>Name</th><th>Contact</th><th>Default address</th><th><span class="visually-hidden">Actions</span></th></tr></thead>
            <tbody>
              @for (customer of customers.value().content; track customer.id) {
                <tr>
                  <td>{{ customer.name }}</td>
                  <td>{{ customer.email || '–' }}<span class="cell-sub mono">{{ customer.phone }}</span></td>
                  <td>{{ customer.defaultAddress?.normalizedAddress || customer.defaultAddress?.rawAddress || '–' }}</td>
                  <td class="row-actions"><button type="button" class="btn btn-small" (click)="openEdit(customer)"><app-icon name="pencil" /> Edit</button></td>
                </tr>
              }
            </tbody>
          </table></div>
          <app-pager [page]="page()" [totalPages]="customers.value().totalPages" [total]="customers.value().totalElements" (pageChange)="page.set($event)" />
        }
      } @else {
        <app-load-state [error]="customers.error()" (retry)="customers.reload()" />
      }
    </div>

    <app-modal [open]="dialogOpen()" [heading]="editing() ? 'Edit customer' : 'New customer'" (closed)="dialogOpen.set(false)">
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (runner.error()) { <div class="banner banner-error" role="alert" style="margin-bottom:12px">{{ runner.error() }}</div> }
        <div class="form-grid">
          <app-field class="span-2" label="Name" [control]="form.controls.name"><input class="input" formControlName="name" /></app-field>
          <app-field label="Phone" [optional]="true" hint="With country code, e.g. +14165550123" patternMessage="Use the format +14165550123" [control]="form.controls.phone"><input class="input" type="tel" formControlName="phone" /></app-field>
          <app-field label="Email" [optional]="true" [control]="form.controls.email"><input class="input" type="email" formControlName="email" /></app-field>
          <div class="field span-2">
            <span class="field-label">Default address <span class="muted">(optional)</span></span>
            @if (editing()?.defaultAddress && !address()) {
              <p class="small muted">Now: {{ editing()?.defaultAddress?.normalizedAddress || editing()?.defaultAddress?.rawAddress }}. Search below to replace it.</p>
            }
            <app-address-picker [choice]="address()" (choiceChange)="address.set($event)" />
          </div>
        </div>
        <div class="form-actions">
          <button type="button" class="btn" (click)="dialogOpen.set(false)">Cancel</button>
          <button class="btn btn-primary" [disabled]="runner.busy()">{{ runner.busy() ? 'Saving…' : 'Save customer' }}</button>
        </div>
      </form>
    </app-modal>
  `,
})
export class Customers {
  private readonly api = inject(Api);
  private readonly ctx = inject(CompanyContext);

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly page = signal(0);
  protected readonly term = toSignal(
    this.search.valueChanges.pipe(
      map((value) => value.trim()),
      debounceTime(300),
      distinctUntilChanged(),
      tap(() => this.page.set(0)),
      startWith(''),
    ),
    { initialValue: '' },
  );

  protected readonly customers = rxResource({
    params: () => ({ id: this.ctx.id(), page: this.page(), search: this.term() }),
    stream: ({ params }) => this.api.customers(params.id, { page: params.page, search: params.search, size: 50 }),
  });

  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<Customer | null>(null);
  protected readonly address = signal<AddressChoice | null>(null);
  protected readonly runner = new FormRunner();
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(254)]],
    phone: ['', Validators.pattern(E164)],
    email: ['', [Validators.email, Validators.maxLength(254)]],
  });

  protected openNew(): void { this.open(null); }
  protected openEdit(customer: Customer): void { this.open(customer); }

  private open(customer: Customer | null): void {
    this.editing.set(customer);
    this.address.set(null);
    this.form.reset({ name: customer?.name ?? '', phone: customer?.phone ?? '', email: customer?.email ?? '' });
    this.runner.reset();
    this.dialogOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    // Blank means "not sent": the phone pattern rejects "", and PATCH treats a missing field as unchanged
    const body: CustomerRequest = { name: value.name, phone: value.phone || undefined, email: value.email || undefined };

    const editing = this.editing();
    const request = resolveAddressId(this.api, this.ctx.id(), this.address()).pipe(switchMap((defaultAddressId) => {
      const payload = { ...body, defaultAddressId };
      return editing ? this.api.updateCustomer(editing.id, payload) : this.api.createCustomer(this.ctx.id(), payload);
    }));
    this.runner.run(request, () => {
      this.dialogOpen.set(false);
      this.customers.reload();
    }, this.form);
  }
}
