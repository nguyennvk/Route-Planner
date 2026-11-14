import { Component, computed, input, model } from '@angular/core';
import { VEHICLE_TYPES, VehicleType } from '../../core/models';
import {
  DEFAULT_PLAN_SETTINGS, MoneyField, PlanSettings, fuelError, isDefault, moneyError, settingsValid,
} from './plan-settings';

const FUEL_HINTS: Record<VehicleType, string> = {
  motorbike: 'About 3 L per 100 km',
  car: 'About 7 L per 100 km',
  van: 'About 11 L per 100 km',
  truck: 'About 22 L per 100 km',
};

// The owner's prices and rules for the solver. Collapsed by default: most solves use the saved choice.
// Changes go out through the settings model, so the page decides where they're kept.
@Component({
  selector: 'app-plan-preferences',
  template: `
    <details class="card prefs">
      <summary>
        <span class="section-title">Costs and rules</span>
        <span class="badge" [class.badge-warn]="!valid()">{{ !valid() ? 'Needs a fix' : (custom() ? 'Custom' : 'Defaults') }}</span>
      </summary>

      <div class="body">
        <p class="small muted intro">The solver keeps the rules first, then delivers as many orders as it can, then picks the cheapest plan using these prices.</p>

        <fieldset>
          <legend>Costs</legend>
          <div class="pair">
            <label class="field">
              <span class="field-label">Driver wage, $ per hour</span>
              <input class="input" type="number" min="0" step="0.5" inputmode="decimal"
                     [value]="settings().wagePerHour" (input)="setMoney('wagePerHour', $event)"
                     [attr.aria-invalid]="!!error('wagePerHour')" aria-describedby="wage-hint" />
              <span class="field-hint" id="wage-hint">Paid from leaving the depot until getting back, waiting included.</span>
              @if (error('wagePerHour'); as message) { <span class="field-error">{{ message }}</span> }
            </label>
            <label class="field">
              <span class="field-label">Overtime, $ per hour</span>
              <input class="input" type="number" min="0" step="0.5" inputmode="decimal"
                     [value]="settings().overtimePerHour" (input)="setMoney('overtimePerHour', $event)"
                     [attr.aria-invalid]="!!error('overtimePerHour')" aria-describedby="overtime-hint" />
              <span class="field-hint" id="overtime-hint">For time after the shift ends. Often 1.5× the wage.</span>
              @if (error('overtimePerHour'); as message) { <span class="field-error">{{ message }}</span> }
            </label>
          </div>

          <p class="field-label fuel-title">Fuel, $ per km</p>
          <div class="pair">
            @for (type of fuelTypes(); track type) {
              <label class="field">
                <span class="field-label type">{{ type }}</span>
                <input class="input" type="number" min="0" step="0.01" inputmode="decimal"
                       [value]="settings().fuelPerKm[type]" (input)="setFuel(type, $event)"
                       [attr.aria-invalid]="!!fuelMessage(type)" [attr.aria-describedby]="'fuel-hint-' + type" />
                <span class="field-hint" [id]="'fuel-hint-' + type">{{ fuelHints[type] }}</span>
                @if (fuelMessage(type); as message) { <span class="field-error">{{ message }}</span> }
              </label>
            }
          </div>
        </fieldset>

        <fieldset>
          <legend>Vehicle weight limit</legend>
          <label class="check option">
            <input type="radio" name="capacity" [checked]="settings().capacity === 'strict'" (change)="set('capacity', 'strict')" />
            <span><strong>Never overload</strong>
              <span class="field-hint">A vehicle never carries more than its limit, even if that leaves orders for another day.</span></span>
          </label>
          <label class="check option">
            <input type="radio" name="capacity" [checked]="settings().capacity === 'flexible'" (change)="set('capacity', 'flexible')" />
            <span><strong>Allow small overloads</strong>
              <span class="field-hint">Only to fit in an order that would otherwise be left out. Each kg over counts like one more order left out, so overloads stay small.</span></span>
          </label>
        </fieldset>

        <fieldset>
          <legend>Orders that are costly to deliver</legend>
          <label class="check option">
            <input type="radio" name="unfit" [checked]="settings().unfitOrders === 'deliverAll'" (change)="set('unfitOrders', 'deliverAll')" />
            <span><strong>Deliver as many as possible</strong>
              <span class="field-hint">Every order that fits is delivered, whatever it costs in fuel and time.</span></span>
          </label>
          <label class="check option">
            <input type="radio" name="unfit" [checked]="settings().unfitOrders === 'skipExpensive'" (change)="set('unfitOrders', 'skipExpensive')" />
            <span><strong>Skip orders that cost too much</strong>
              <span class="field-hint">Leave an order pending for another day when delivering it adds more than this amount.</span></span>
          </label>
          @if (settings().unfitOrders === 'skipExpensive') {
            <label class="field indent">
              <span class="field-label">Skip when one order costs more than, $</span>
              <input class="input" type="number" min="1" step="1" inputmode="decimal"
                     [value]="settings().skipAbove" (input)="setMoney('skipAbove', $event)"
                     [attr.aria-invalid]="!!error('skipAbove')" />
              @if (error('skipAbove'); as message) { <span class="field-error">{{ message }}</span> }
            </label>
          }
        </fieldset>

        <fieldset>
          <legend>Late deliveries</legend>
          <label class="field">
            <span class="field-label">Cost of each minute late, $</span>
            <input class="input" type="number" min="0" step="0.25" inputmode="decimal"
                   [value]="settings().latePerMinute" (input)="setMoney('latePerMinute', $event)"
                   [attr.aria-invalid]="!!error('latePerMinute')" aria-describedby="late-hint" />
            <span class="field-hint" id="late-hint">For orders with a flexible window. Higher means more driving to arrive on time; 0 ignores lateness. Strict windows are never missed on purpose.</span>
            @if (error('latePerMinute'); as message) { <span class="field-error">{{ message }}</span> }
          </label>
        </fieldset>

        <div class="foot">
          <span class="small muted">Saved in this browser for this company.</span>
          @if (custom()) { <button type="button" class="link-btn" (click)="reset()">Reset to defaults</button> }
        </div>
      </div>
    </details>
  `,
  styles: `
    .prefs summary { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 13px 14px 11px; cursor: pointer; list-style: none; }
    .prefs summary::-webkit-details-marker { display: none; }
    .prefs summary::after { content: '▾'; color: var(--ink-muted); margin-left: auto; }
    .prefs[open] summary::after { content: '▴'; }
    .prefs summary .badge { margin-left: auto; }
    .body { display: flex; flex-direction: column; gap: 16px; padding: 0 14px 14px; border-top: 1px solid var(--line); }
    .intro { margin-top: 10px; }
    fieldset { display: flex; flex-direction: column; gap: 10px; margin: 0; padding: 0; border: 0; min-width: 0; }
    legend { padding: 0; margin-bottom: 8px; font-weight: 700; font-size: 14px; }
    .pair { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
    .fuel-title { margin-top: 2px; }
    .type { text-transform: capitalize; font-weight: 500; }
    .option strong { display: block; font-weight: 600; }
    .option .field-hint { display: block; }
    .indent { margin-left: 24px; }
    .foot { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; padding-top: 10px; border-top: 1px solid var(--line); }
  `,
})
export class PlanPreferences {
  readonly settings = model.required<PlanSettings>();
  // Fuel inputs only for the kinds of vehicle the company has; all four if it has none yet
  readonly vehicleTypes = input<VehicleType[]>([]);

  protected readonly fuelHints = FUEL_HINTS;
  protected readonly fuelTypes = computed(() => {
    const used = new Set(this.vehicleTypes());
    return used.size ? VEHICLE_TYPES.filter((type) => used.has(type)) : VEHICLE_TYPES;
  });
  protected readonly custom = computed(() => !isDefault(this.settings()));
  protected readonly valid = computed(() => settingsValid(this.settings(), this.fuelTypes()));

  protected error(field: MoneyField): string | null {
    return moneyError(field, this.settings()[field]);
  }

  protected fuelMessage(type: VehicleType): string | null {
    return fuelError(this.settings().fuelPerKm[type]);
  }

  // valueAsNumber is NaN for an empty box; kept as is so the field shows "Enter an amount"
  protected setMoney(field: MoneyField, event: Event): void {
    const value = (event.target as HTMLInputElement).valueAsNumber;
    this.settings.update((current) => ({ ...current, [field]: value }));
  }

  protected setFuel(type: VehicleType, event: Event): void {
    const value = (event.target as HTMLInputElement).valueAsNumber;
    this.settings.update((current) => ({ ...current, fuelPerKm: { ...current.fuelPerKm, [type]: value } }));
  }

  protected set<K extends 'capacity' | 'unfitOrders'>(key: K, value: PlanSettings[K]): void {
    this.settings.update((current) => ({ ...current, [key]: value }));
  }

  protected reset(): void {
    this.settings.set(DEFAULT_PLAN_SETTINGS);
  }
}
