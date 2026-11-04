import { Component, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { debounceTime, distinctUntilChanged, map, startWith, tap } from 'rxjs';
import { Api } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { Address } from '../../core/models';
import { AddressChoice, AddressPicker } from '../../shared/address-picker';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { LatLng, PinMap } from '../../shared/map/pin-map';
import { Modal } from '../../shared/modal';
import { Pager } from '../../shared/pager';

const SOURCE_LABEL: Record<Address['geocodeSource'], string> = {
  geocodio: 'Geocoded', nominatim: 'Geocoded', google: 'Geocoded', manual_pin: 'Manual pin',
};

@Component({
  selector: 'app-addresses',
  imports: [ReactiveFormsModule, AddressPicker, Icon, LoadState, Modal, PinMap, Pager],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Addresses</h1><p>Every delivery point and depot. If a geocoded point is off, move its pin and the next plan uses the new spot.</p></div>
        @if (ctx.isOwner()) { <button type="button" class="btn btn-primary" (click)="openNew()"><app-icon name="plus" /> New address</button> }
      </div>
      <div class="toolbar">
        <input class="input" style="min-width:280px" [formControl]="search" type="search" placeholder="Search addresses" aria-label="Search addresses" />
      </div>

      @if (addresses.hasValue()) {
        @if (addresses.value().content.length === 0) {
          <div class="card empty"><h2>{{ term() ? 'No addresses match that search' : 'No addresses yet' }}</h2></div>
        } @else {
          <div class="table-wrap"><table class="data">
            <thead><tr><th>Address</th><th>Location</th><th>Source</th>@if (ctx.isOwner()) { <th><span class="visually-hidden">Actions</span></th> }</tr></thead>
            <tbody>
              @for (address of addresses.value().content; track address.id) {
                <tr>
                  <td>{{ address.normalizedAddress || address.rawAddress }}</td>
                  <td class="mono nowrap">{{ address.latitude.toFixed(5) }}, {{ address.longitude.toFixed(5) }}</td>
                  <td><span class="badge" [class.badge-info]="address.geocodeSource === 'manual_pin'">{{ sourceLabel[address.geocodeSource] }}</span></td>
                  @if (ctx.isOwner()) { <td class="row-actions"><button type="button" class="btn btn-small" (click)="openPin(address)"><app-icon name="pin" /> Move pin</button></td> }
                </tr>
              }
            </tbody>
          </table></div>
          <app-pager [page]="page()" [totalPages]="addresses.value().totalPages" [total]="addresses.value().totalElements" (pageChange)="page.set($event)" />
        }
      } @else {
        <app-load-state [error]="addresses.error()" (retry)="addresses.reload()" />
      }
    </div>

    <app-modal [open]="moving() !== null" heading="Move pin" (closed)="moving.set(null)">
      @if (moving(); as address) {
        <p class="small muted" style="margin-bottom:10px">{{ address.normalizedAddress || address.rawAddress }}</p>
        @if (runner.error()) { <div class="banner banner-error" role="alert" style="margin-bottom:10px">{{ runner.error() }}</div> }
        <app-pin-map [pin]="pin()" (pinChange)="pin.set($event)" />
        <div class="coords">
          <label class="field"><span class="field-label">Latitude</span><input class="input" type="number" step="0.000001" [value]="pin()?.latitude" (change)="setCoord('latitude', $any($event.target).value)" /></label>
          <label class="field"><span class="field-label">Longitude</span><input class="input" type="number" step="0.000001" [value]="pin()?.longitude" (change)="setCoord('longitude', $any($event.target).value)" /></label>
        </div>
        <div class="form-actions">
          <button type="button" class="btn" (click)="moving.set(null)">Cancel</button>
          <button type="button" class="btn btn-primary" [disabled]="runner.busy()" (click)="savePin(address)">{{ runner.busy() ? 'Saving…' : 'Save pin' }}</button>
        </div>
      }
    </app-modal>

    <app-modal [open]="adding()" heading="New address" (closed)="adding.set(false)">
      @if (adding()) {
        @if (runner.error()) { <div class="banner banner-error" role="alert" style="margin-bottom:10px">{{ runner.error() }}</div> }
        <app-address-picker [allowSaved]="false" [choice]="choice()" (choiceChange)="choice.set($event)" />
        <div class="form-actions">
          <button type="button" class="btn" (click)="adding.set(false)">Cancel</button>
          <button type="button" class="btn btn-primary" [disabled]="!choice() || runner.busy()" (click)="saveNew()">{{ runner.busy() ? 'Saving…' : 'Save address' }}</button>
        </div>
      }
    </app-modal>
  `,
  styles: `.coords { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 10px; }`,
})
export class Addresses {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);

  protected readonly sourceLabel = SOURCE_LABEL;
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly page = signal(0);
  protected readonly term = toSignal(
    this.search.valueChanges.pipe(
      map((value) => value.trim()), debounceTime(300), distinctUntilChanged(), tap(() => this.page.set(0)), startWith(''),
    ),
    { initialValue: '' },
  );
  protected readonly addresses = rxResource({
    params: () => ({ id: this.ctx.id(), page: this.page(), search: this.term() }),
    stream: ({ params }) => this.api.addresses(params.id, { page: params.page, search: params.search, size: 50 }),
  });

  protected readonly runner = new FormRunner();
  protected readonly moving = signal<Address | null>(null);
  protected readonly pin = signal<LatLng | null>(null);
  protected readonly adding = signal(false);
  protected readonly choice = signal<AddressChoice | null>(null);

  protected openPin(address: Address): void {
    this.runner.reset();
    this.pin.set({ latitude: address.latitude, longitude: address.longitude });
    this.moving.set(address);
  }

  protected setCoord(key: keyof LatLng, value: string): void {
    const number = Number(value);
    if (value === '' || Number.isNaN(number)) return;
    this.pin.update((pin) => (pin ? { ...pin, [key]: number } : pin));
  }

  protected savePin(address: Address): void {
    const pin = this.pin();
    if (!pin) return;
    this.runner.run(this.api.movePin(address.id, pin), () => {
      this.moving.set(null);
      this.addresses.reload();
    });
  }

  protected openNew(): void {
    this.runner.reset();
    this.choice.set(null);
    this.adding.set(true);
  }

  protected saveNew(): void {
    const choice = this.choice();
    if (choice?.kind !== 'new') return;
    this.runner.run(this.api.createAddress(this.ctx.id(), choice.request), () => {
      this.adding.set(false);
      this.addresses.reload();
    });
  }
}
