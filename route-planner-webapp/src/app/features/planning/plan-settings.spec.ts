import { beforeEach, describe, expect, it } from 'vitest';
import { Shift, Vehicle } from '../../core/models';
import { DEFAULT_PLAN_SETTINGS, PlanSettings, readPlanSettings, settingsValid, toSolveRequest, writePlanSettings } from './plan-settings';

const vehicle = (id: number, type: Vehicle['type']): Vehicle => ({ id, name: `V${id}`, type, capacityKg: 100, capacityM3: null, homeDepotId: 1, active: true });
const shift = (id: number, vehicleId: number): Shift => ({ id, vehicleId, vehicleName: '', driverUserId: null, driverName: null, startAt: '', endAt: '', published: false });

describe('toSolveRequest', () => {
  it('prices every shift in cents by its vehicle type', () => {
    const { planPreferences } = toSolveRequest(DEFAULT_PLAN_SETTINGS, [shift(20, 1), shift(21, 2)], [vehicle(1, 'van'), vehicle(2, 'truck')]);
    expect(planPreferences).toEqual({
      20: { fuelCentsPerKm: 20, wageCentsPerHour: 2000, overtimeCentsPerHour: 3000 },
      21: { fuelCentsPerKm: 40, wageCentsPerHour: 2000, overtimeCentsPerHour: 3000 },
    });
  });

  it('leaves capacity and missing orders at the API default, and always sends the late price', () => {
    const { planScorePreference } = toSolveRequest(DEFAULT_PLAN_SETTINGS, [], []);
    expect(planScorePreference).toEqual({ capacity: null, missingOrder: null, late: { type: 'SOFT', weight: 100 } });
  });

  it('turns the owner choices into levels and weights', () => {
    const settings: PlanSettings = { ...DEFAULT_PLAN_SETTINGS, capacity: 'flexible', unfitOrders: 'skipExpensive', skipAbove: 12.5, latePerMinute: 0 };
    expect(toSolveRequest(settings, [], []).planScorePreference).toEqual({
      capacity: { type: 'MEDIUM', weight: 1 },
      missingOrder: { type: 'SOFT', weight: 1250 },
      late: { type: 'SOFT', weight: 0 },
    });
  });
});

describe('settingsValid', () => {
  it('rejects empty, negative and too-large amounts', () => {
    expect(settingsValid(DEFAULT_PLAN_SETTINGS, ['van'])).toBe(true);
    expect(settingsValid({ ...DEFAULT_PLAN_SETTINGS, wagePerHour: Number.NaN }, ['van'])).toBe(false);
    expect(settingsValid({ ...DEFAULT_PLAN_SETTINGS, latePerMinute: -1 }, ['van'])).toBe(false);
    expect(settingsValid({ ...DEFAULT_PLAN_SETTINGS, wagePerHour: 2000 }, ['van'])).toBe(false);
  });

  it('only checks the skip amount when skipping is on, and only the fuel of vehicles in use', () => {
    expect(settingsValid({ ...DEFAULT_PLAN_SETTINGS, skipAbove: 0 }, ['van'])).toBe(true);
    expect(settingsValid({ ...DEFAULT_PLAN_SETTINGS, unfitOrders: 'skipExpensive', skipAbove: 0 }, ['van'])).toBe(false);
    const badTruck = { ...DEFAULT_PLAN_SETTINGS, fuelPerKm: { ...DEFAULT_PLAN_SETTINGS.fuelPerKm, truck: -1 } };
    expect(settingsValid(badTruck, ['van'])).toBe(true);
    expect(settingsValid(badTruck, ['van', 'truck'])).toBe(false);
  });
});

describe('readPlanSettings', () => {
  beforeEach(() => localStorage.clear());

  it('gives the defaults when nothing is saved', () => {
    expect(readPlanSettings(1)).toEqual(DEFAULT_PLAN_SETTINGS);
  });

  it('keeps each company separate and fills gaps from the defaults', () => {
    writePlanSettings(1, { ...DEFAULT_PLAN_SETTINGS, wagePerHour: 25 });
    localStorage.setItem('route-scheduler.plan-settings.2', JSON.stringify({ wagePerHour: null, capacity: 'flexible' }));
    expect(readPlanSettings(1).wagePerHour).toBe(25);
    expect(readPlanSettings(2)).toEqual({ ...DEFAULT_PLAN_SETTINGS, capacity: 'flexible' });
  });
});
