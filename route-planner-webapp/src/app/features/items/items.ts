import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { Item, ItemRequest } from '../../core/models';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { Modal } from '../../shared/modal';
import { Pager } from '../../shared/pager';

@Component({
  selector: 'app-items',
  imports: [ReactiveFormsModule, Field, Icon, LoadState, Modal, Pager],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Items</h1><p>What you deliver. An order's weight is worked out from these.</p></div>
        @if (ctx.isOwner()) { <button type="button" class="btn btn-primary" (click)="openNew()"><app-icon name="plus" /> New item</button> }
      </div>

      @if (items.hasValue()) {
        @if (items.value().content.length === 0) {
          <div class="card empty"><h2>No items yet</h2><p>Add the products you deliver, each with its weight per unit.</p></div>
        } @else {
          <div class="table-wrap"><table class="data">
            <thead><tr><th>Name</th><th>SKU</th><th class="num">Weight per unit</th><th class="num">Volume per unit</th>@if (ctx.isOwner()) { <th><span class="visually-hidden">Actions</span></th> }</tr></thead>
            <tbody>
              @for (item of items.value().content; track item.id) {
                <tr>
                  <td>{{ item.name }}</td>
                  <td class="mono">{{ item.sku || '–' }}</td>
                  <td class="num nowrap">{{ item.weightKg }} kg</td>
                  <td class="num nowrap">{{ item.volumeM3 ? item.volumeM3 + ' m³' : '–' }}</td>
                  @if (ctx.isOwner()) { <td class="row-actions"><button type="button" class="btn btn-small" (click)="openEdit(item)"><app-icon name="pencil" /> Edit</button></td> }
                </tr>
              }
            </tbody>
          </table></div>
          <app-pager [page]="page()" [totalPages]="items.value().totalPages" [total]="items.value().totalElements" (pageChange)="page.set($event)" />
        }
      } @else {
        <app-load-state [error]="items.error()" (retry)="items.reload()" />
      }
    </div>

    <app-modal [open]="dialogOpen()" [heading]="editing() ? 'Edit item' : 'New item'" (closed)="dialogOpen.set(false)">
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (runner.error()) { <div class="banner banner-error" role="alert" style="margin-bottom:12px">{{ runner.error() }}</div> }
        <div class="form-grid">
          <app-field class="span-2" label="Name" [control]="form.controls.name"><input class="input" formControlName="name" /></app-field>
          <app-field label="Weight per unit (kg)" [control]="form.controls.weightKg"><input class="input" type="number" step="0.001" min="0" formControlName="weightKg" /></app-field>
          <app-field label="Volume per unit (m³)" [optional]="true" [control]="form.controls.volumeM3"><input class="input" type="number" step="0.0001" min="0" formControlName="volumeM3" /></app-field>
          <app-field class="span-2" label="SKU" [optional]="true" hint="Unique within your company" [control]="form.controls.sku"><input class="input" formControlName="sku" /></app-field>
        </div>
        <div class="form-actions">
          <button type="button" class="btn" (click)="dialogOpen.set(false)">Cancel</button>
          <button class="btn btn-primary" [disabled]="runner.busy()">{{ runner.busy() ? 'Saving…' : 'Save item' }}</button>
        </div>
      </form>
    </app-modal>
  `,
})
export class Items {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);

  protected readonly page = signal(0);
  protected readonly items = rxResource({
    params: () => ({ id: this.ctx.id(), page: this.page() }),
    stream: ({ params }) => this.api.items(params.id, { page: params.page, size: 50 }),
  });

  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<Item | null>(null);
  protected readonly runner = new FormRunner();
  protected readonly form = inject(FormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(254)]],
    weightKg: [null as number | null, [Validators.required, Validators.min(0.001)]],
    volumeM3: [null as number | null, Validators.min(0.0001)],
    sku: ['', Validators.maxLength(64)],
  });

  protected openNew(): void {
    this.editing.set(null);
    this.form.reset({ name: '', weightKg: null, volumeM3: null, sku: '' });
    this.runner.reset();
    this.dialogOpen.set(true);
  }

  protected openEdit(item: Item): void {
    this.editing.set(item);
    this.form.reset({ name: item.name, weightKg: item.weightKg, volumeM3: item.volumeM3, sku: item.sku ?? '' });
    this.runner.reset();
    this.dialogOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const editing = this.editing();
    const body: ItemRequest = { name: value.name!, weightKg: value.weightKg!, volumeM3: value.volumeM3 ?? undefined };
    // UpdateItemRequest: "" removes the SKU while a missing field leaves it. On create, blank means none.
    if (value.sku || editing) body.sku = value.sku ?? '';
    const request = editing ? this.api.updateItem(editing.id, body) : this.api.createItem(this.ctx.id(), body);
    this.runner.run(request, () => {
      this.dialogOpen.set(false);
      this.items.reload();
    }, this.form);
  }
}
