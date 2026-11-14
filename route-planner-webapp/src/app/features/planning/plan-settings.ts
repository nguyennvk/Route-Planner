import { PlanRequest, RuleWeight, Shift, ShiftPrices, Vehicle, VehicleType } from '../../core/models';

// What the owner sets before solving. Money is in dollars here, as people type it; the API wants cents.
export interface PlanSettings {
  wagePerHour: number;
  overtimePerHour: number;
  fuelPerKm: Record<VehicleType, number>;
  capacity: 'strict' | 'flexible';
  unfitOrders: 'deliverAll' | 'skipExpensive';
  skipAbove: number;          // dollars per order, used when unfitOrders is 'skipExpensive'
  latePerMinute: number;      // dollars per minute late on a flexible window
}

// Rough averages for a small delivery business. Overtime at 1.5× the wage; late at $1/min matches the API's
// own default. Fuel per km: about 3 L/100 km for a motorbike, 7 for a car, 11 for a van, 22 for a truck,
// at roughly $1.80 per litre.
export const DEFAULT_PLAN_SETTINGS: PlanSettings = {
  wagePerHour: 20,
  overtimePerHour: 30,
  fuelPerKm: { motorbike: 0.05, car: 0.12, van: 0.2, truck: 0.4 },
  capacity: 'strict',
  unfitOrders: 'deliverAll',
  skipAbove: 25,
  latePerMinute: 1,
};

export type MoneyField = 'wagePerHour' | 'overtimePerHour' | 'skipAbove' | 'latePerMinute';

// Upper bounds catch typos (2000 instead of 20.00) before they skew a whole plan
const LIMITS: Record<MoneyField, number> = { wagePerHour: 1000, overtimePerHour: 1000, skipAbove: 10000, latePerMinute: 100 };
const FUEL_LIMIT = 10;

export function moneyError(field: MoneyField, value: number): string | null {
  if (!Number.isFinite(value)) return 'Enter an amount.';
  if (value < 0) return "Can't be negative.";
  if (field === 'skipAbove' && value === 0) return 'Must be more than $0, or every order would be skipped.';
  if (value > LIMITS[field]) return `Must be $${LIMITS[field]} or less.`;
  return null;
}

export function fuelError(value: number): string | null {
  if (!Number.isFinite(value)) return 'Enter an amount.';
  if (value < 0) return "Can't be negative.";
  if (value > FUEL_LIMIT) return `Must be $${FUEL_LIMIT} or less per km.`;
  return null;
}

// Only prices that will be used count: a bad fuel price for a vehicle type nobody has doesn't block solving
export function settingsValid(settings: PlanSettings, fuelTypes: readonly VehicleType[]): boolean {
  const money: MoneyField[] = ['wagePerHour', 'overtimePerHour', 'latePerMinute'];
  if (settings.unfitOrders === 'skipExpensive') money.push('skipAbove');
  return money.every((field) => moneyError(field, settings[field]) === null)
    && fuelTypes.every((type) => fuelError(settings.fuelPerKm[type]) === null);
}

export function isDefault(settings: PlanSettings): boolean {
  return JSON.stringify(settings) === JSON.stringify(DEFAULT_PLAN_SETTINGS);
}

// The API has nowhere to keep these yet, so each company's choice lives in this browser
const storageKey = (companyId: number) => `route-scheduler.plan-settings.${companyId}`;

export function readPlanSettings(companyId: number): PlanSettings {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey(companyId)) ?? 'null') as Partial<PlanSettings> | null;
    if (!saved) return DEFAULT_PLAN_SETTINGS;
    // Merge over the defaults, so a copy saved before a setting existed still has every field.
    // An empty input is saved as null, which falls back to the default too.
    const fuelPerKm = { ...DEFAULT_PLAN_SETTINGS.fuelPerKm };
    for (const type of Object.keys(fuelPerKm) as VehicleType[]) {
      fuelPerKm[type] = finiteOr(saved.fuelPerKm?.[type], fuelPerKm[type]);
    }
    return {
      wagePerHour: finiteOr(saved.wagePerHour, DEFAULT_PLAN_SETTINGS.wagePerHour),
      overtimePerHour: finiteOr(saved.overtimePerHour, DEFAULT_PLAN_SETTINGS.overtimePerHour),
      fuelPerKm,
      capacity: saved.capacity === 'flexible' ? 'flexible' : 'strict',
      unfitOrders: saved.unfitOrders === 'skipExpensive' ? 'skipExpensive' : 'deliverAll',
      skipAbove: finiteOr(saved.skipAbove, DEFAULT_PLAN_SETTINGS.skipAbove),
      latePerMinute: finiteOr(saved.latePerMinute, DEFAULT_PLAN_SETTINGS.latePerMinute),
    };
  } catch {
    return DEFAULT_PLAN_SETTINGS;
  }
}

export function writePlanSettings(companyId: number, settings: PlanSettings): void {
  localStorage.setItem(storageKey(companyId), JSON.stringify(settings));
}

// The settings as the API wants them: prices per shift in cents, plus overrides for the rules.
// A rule left at its default is left out, so the API's own default applies.
export function toSolveRequest(
  settings: PlanSettings, shifts: Shift[], vehicles: Vehicle[],
): Pick<PlanRequest, 'planPreferences' | 'planScorePreference'> {
  const typeById = new Map(vehicles.map((vehicle) => [vehicle.id, vehicle.type]));
  const planPreferences: Record<number, ShiftPrices> = {};
  for (const shift of shifts) {
    // A vehicle missing from the list (deleted since the shift was made) is priced as a van
    const type = typeById.get(shift.vehicleId) ?? 'van';
    planPreferences[shift.id] = {
      fuelCentsPerKm: cents(settings.fuelPerKm[type]),
      wageCentsPerHour: cents(settings.wagePerHour),
      overtimeCentsPerHour: cents(settings.overtimePerHour),
    };
  }

  // Capacity on medium at 1 per kg: a kg over weighs the same as one order left out
  const capacity: RuleWeight | null = settings.capacity === 'flexible' ? { type: 'MEDIUM', weight: 1 } : null;
  // On the soft level, which is money: leaving an order out costs this many cents
  const missingOrder: RuleWeight | null = settings.unfitOrders === 'skipExpensive' ? { type: 'SOFT', weight: cents(settings.skipAbove) } : null;
  const late: RuleWeight = { type: 'SOFT', weight: cents(settings.latePerMinute) };

  return { planPreferences, planScorePreference: { capacity, missingOrder, late } };
}

function cents(dollars: number): number {
  return Math.round(dollars * 100);
}

function finiteOr(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}
