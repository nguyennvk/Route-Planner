import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Observable, catchError, debounceTime, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { Api } from '../core/api';
import { CompanyContext } from '../core/company-context';
import { errorMessage } from '../core/errors';
import { Address, AddressSuggestion, NewAddress } from '../core/models';
import { Icon } from './icon';
import { LatLng, PinMap } from './map/pin-map';

// A saved address of this company, or a new one that still has to be POSTed
export type AddressChoice = { kind: 'saved'; address: Address } | { kind: 'new'; request: NewAddress; label: string };

// A new address has to exist before a customer or order can point at it
export function resolveAddressId(api: Api, companyId: number, choice: AddressChoice | null): Observable<number | undefined> {
  if (!choice) return of(undefined);
  if (choice.kind === 'saved') return of(choice.address.id);
  return api.createAddress(companyId, choice.request).pipe(map((address) => address.id));
}

interface Lookup { saved: Address[]; suggestions: AddressSuggestion[]; error: string | null; searching: boolean; finished: boolean; }
const IDLE: Lookup = { saved: [], suggestions: [], error: null, searching: false, finished: false };
const MIN_QUERY = 5;

// The API's two ways to create an address. A precise suggestion carries a signed token. Anything else
// (an imprecise match, or no match) needs someone to place a pin on the map, as PinValidation expects.
@Component({
  selector: 'app-address-picker',
  imports: [ReactiveFormsModule, Icon, PinMap],
  template: `
    @if (choiceLabel(); as label) {
      <div class="chosen">
        <app-icon name="pin" />
        <span>{{ label }}</span>
        <button type="button" class="btn btn-small" (click)="clear()">Change</button>
      </div>
    } @else if (pinMode()) {
      <div class="pin-panel">
        <p class="small muted">Click the map or drag the pin to the exact entrance. Pins must be in Canada.</p>
        <label class="field">
          <span class="field-label">Address label</span>
          <input class="input" [formControl]="pinLabel" placeholder="45 Adamson St, Mississauga" />
        </label>
        <app-pin-map [pin]="pin()" (pinChange)="setPin($event)" />
        <div class="coords">
          <label class="field"><span class="field-label">Latitude</span>
            <input class="input" type="number" step="0.000001" [value]="pin()?.latitude ?? ''" (change)="setCoord('latitude', $any($event.target).value)" /></label>
          <label class="field"><span class="field-label">Longitude</span>
            <input class="input" type="number" step="0.000001" [value]="pin()?.longitude ?? ''" (change)="setCoord('longitude', $any($event.target).value)" /></label>
        </div>
        <div class="pin-actions">
          <button type="button" class="btn" (click)="pinMode.set(false)">Back to search</button>
          <button type="button" class="btn btn-primary" [disabled]="!pinValid()" (click)="usePin()">Use this pin</button>
        </div>
      </div>
    } @else {
      <div class="search">
        <app-icon name="search" class="search-icon" />
        <input class="input" [formControl]="query" placeholder="Start typing a Canadian address" autocomplete="off" [attr.aria-label]="ariaLabel()" />
      </div>
      @if (lookup().searching) { <p class="small muted status" role="status">Searching…</p> }
      @if (lookup().error) { <p class="field-error status" role="alert">{{ lookup().error }}</p> }
      @if (lookup().finished && lookup().suggestions.length === 0 && !lookup().error) {
        <p class="small muted status" role="status">No new matches. Include the city and province, like "1 Dundas St W, Toronto, ON", or place a pin.</p>
      }

      @if (hasResults()) {
        <ul class="results">
          @for (address of lookup().saved; track address.id) {
            <li><button type="button" (click)="pickSaved(address)">
              <span>{{ address.normalizedAddress || address.rawAddress }}</span>
              <span class="badge">Saved</span>
            </button></li>
          }
          @for (suggestion of lookup().suggestions; track suggestion.formattedAddress) {
            <li><button type="button" (click)="pickSuggestion(suggestion)">
              <span>{{ suggestion.formattedAddress }}</span>
              @if (!suggestion.precise) { <span class="badge badge-warn">Needs a pin</span> }
            </button></li>
          }
        </ul>
      }
      <button type="button" class="btn btn-quiet btn-small pin-link" (click)="openPin()"><app-icon name="pin" /> Place a pin on the map instead</button>
    }
  `,
  styles: `
    :host { display: block; }
    .chosen { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border: 1px solid var(--line-strong); border-radius: var(--radius-m); background: var(--gray-50); }
    .chosen span { flex: 1; min-width: 0; }
    .search { position: relative; }
    .search-icon { position: absolute; left: 10px; top: 11px; color: var(--ink-muted); }
    .search .input { padding-left: 32px; }
    .status { margin-top: 6px; }
    .results { margin-top: 6px; border: 1px solid var(--line-strong); border-radius: var(--radius-m); overflow: hidden; }
    .results li + li { border-top: 1px solid var(--line); }
    .results button { display: flex; align-items: center; justify-content: space-between; gap: 10px; width: 100%; padding: 9px 10px 7px; border: 0; background: var(--surface); text-align: left; cursor: pointer; }
    .results button:hover, .results button:focus-visible { background: var(--brand-soft); }
    .pin-link { margin-top: 6px; color: var(--brand); padding-inline: 4px; }
    .pin-panel { display: flex; flex-direction: column; gap: 10px; padding: 12px; border: 1px solid var(--line-strong); border-radius: var(--radius-m); background: var(--gray-50); }
    .coords { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .pin-actions { display: flex; justify-content: flex-end; gap: 8px; }
  `,
})
export class AddressPicker {
  private readonly api = inject(Api);
  private readonly ctx = inject(CompanyContext);

  // Depots always create a new address, since DepotRequest has no field for an existing one
  readonly allowSaved = input(true);
  readonly ariaLabel = input('Search for an address');
  readonly choice = input<AddressChoice | null>(null);
  readonly choiceChange = output<AddressChoice | null>();

  protected readonly query = new FormControl('', { nonNullable: true });
  protected readonly pinLabel = new FormControl('', { nonNullable: true });
  protected readonly pinMode = signal(false);
  protected readonly pin = signal<LatLng | null>(null);
  private readonly pinLabelValue = toSignal(this.pinLabel.valueChanges.pipe(startWith('')), { initialValue: '' });

  protected readonly choiceLabel = computed(() => {
    const choice = this.choice();
    if (!choice) return null;
    return choice.kind === 'saved' ? (choice.address.normalizedAddress || choice.address.rawAddress) : choice.label;
  });

  protected readonly pinValid = computed(() => !!this.pin() && this.pinLabelValue().trim().length > 0);

  // Waits for a typing pause and switchMap drops stale searches. Every lookup is a paid Geocodio call on
  // the server, so this is worth more here than for a local filter.
  protected readonly lookup = toSignal(
    this.query.valueChanges.pipe(
      map((value) => value.trim()),
      debounceTime(500),
      distinctUntilChanged(),
      switchMap((text) => {
        if (text.length < MIN_QUERY) return of(IDLE);
        const saved = this.allowSaved()
          ? this.api.addresses(this.ctx.id(), { search: text, size: 3 }).pipe(map((page) => page.content), catchError(() => of([])))
          : of([]);
        return saved.pipe(
          switchMap((savedAddresses) => this.api.suggestAddresses(text).pipe(
            map((suggestions): Lookup => ({ saved: savedAddresses, suggestions, error: null, searching: false, finished: true })),
            catchError((error: unknown) => of<Lookup>({ saved: savedAddresses, suggestions: [], error: errorMessage(error), searching: false, finished: true })),
            startWith<Lookup>({ saved: savedAddresses, suggestions: [], error: null, searching: true, finished: false }),
          )),
        );
      }),
    ),
    { initialValue: IDLE },
  );

  protected readonly hasResults = computed(() => this.lookup().saved.length + this.lookup().suggestions.length > 0);

  constructor() {
    // Clearing the choice from outside (a reused form) also resets the search
    effect(() => {
      if (!this.choice()) this.pinMode.set(false);
    });
  }

  protected pickSaved(address: Address): void {
    this.choiceChange.emit({ kind: 'saved', address });
  }

  protected pickSuggestion(suggestion: AddressSuggestion): void {
    if (suggestion.precise && suggestion.token) {
      this.choiceChange.emit({ kind: 'new', request: { token: suggestion.token }, label: suggestion.formattedAddress });
    } else {
      this.pinLabel.setValue(suggestion.formattedAddress);
      this.pin.set({ latitude: suggestion.latitude, longitude: suggestion.longitude });
      this.pinMode.set(true);
    }
  }

  protected openPin(): void {
    this.pinLabel.setValue(this.query.value.trim());
    this.pinMode.set(true);
  }

  protected setPin(pin: LatLng): void {
    this.pin.set(pin);
  }

  protected setCoord(key: keyof LatLng, value: string): void {
    const number = Number(value);
    if (value === '' || Number.isNaN(number)) return;
    this.pin.update((pin) => ({ latitude: pin?.latitude ?? 43.65, longitude: pin?.longitude ?? -79.38, [key]: number }));
  }

  protected usePin(): void {
    const pin = this.pin();
    if (!pin) return;
    const rawAddress = this.pinLabel.value.trim();
    this.choiceChange.emit({ kind: 'new', request: { rawAddress, ...pin }, label: `${rawAddress} (pin)` });
  }

  protected clear(): void {
    this.query.setValue('');
    this.choiceChange.emit(null);
  }
}
