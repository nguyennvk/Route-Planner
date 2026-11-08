import { describe, expect, it } from 'vitest';
import { Depot, Order, Plan, Shift, Vehicle } from '../../core/models';
import { ROUTE_COLORS, buildPlanView, parseScore, softCostLabel } from './plan-view';

const order = (id: number, weight: number): Order => ({
  id, totalWeightKg: weight, status: 'pending', items: [], windowStart: null, windowEnd: null, windowStrict: false,
  serviceMinutes: 5, notes: null, createdAt: '',
  customer: { id, name: `Customer ${id}`, phone: null, email: null, defaultAddress: null, createdAt: '' },
  address: { id, rawAddress: `${id} Main St`, normalizedAddress: null, latitude: 43, longitude: -79, geocodeConfidence: null, geocodeSource: 'geocodio', createdAt: '' },
});
const vehicle: Vehicle = { id: 7, name: 'Van 7', type: 'van', capacityKg: 100, capacityM3: null, homeDepotId: 3, active: true };
const depot = { id: 3, name: 'Main', address: {} } as Depot;
const shift: Shift = { id: 20, vehicleId: 7, vehicleName: 'Van 7', driverUserId: 4, driverName: 'Liam', startAt: '', endAt: '' };

const planBase = { planId: 1, name: 'p', status: 'draft' as const, planDate: '2026-11-02' };

const stop = (orderId: number, sequence: number, minutesLate = 0) => ({
  stopId: orderId * 10, sequence, orderId, name: 'raw', arrivalTime: '2026-11-02T09:00:00', departureTime: '2026-11-02T09:05:00', minutesLate,
});

describe('parseScore', () => {
  it('reads each level of a HardMediumSoftScore', () => {
    expect(parseScore('0hard/-2medium/-1037soft')).toEqual({ hard: 0, medium: -2, soft: -1037, feasible: true });
  });
  it('treats any broken hard rule as infeasible', () => {
    expect(parseScore('-15hard/0medium/0soft')?.feasible).toBe(false);
  });
  it('returns null for anything unexpected', () => {
    expect(parseScore('null')).toBeNull();
  });
});

describe('softCostLabel', () => {
  it('reads the score as time', () => {
    expect(softCostLabel(-1037)).toBe('17 min');
    expect(softCostLabel(-7500)).toBe('2 h 5 min');
  });
});

describe('buildPlanView', () => {
  const lookups = { orders: [order(1, 30), order(2, 45.5), order(3, 10)], shifts: [shift], vehicles: [vehicle], depots: [depot] };

  it('joins stops with their orders and adds up the load', () => {
    const plan: Plan = { ...planBase, score: '0hard/0medium/-10soft', unassignedOrderIds: [3], routes: [{ routeId: 1, shiftId: 20, vehicleId: 7, geometry: null, stops: [stop(1, 0), stop(2, 1, 12)] }] };
    const view = buildPlanView(plan, lookups, 'America/Toronto');
    const route = view.routes[0];

    expect(route.vehicleName).toBe('Van 7');
    expect(route.driverName).toBe('Liam');
    expect(route.depot).toBe(depot);
    expect(route.usedKg).toBe(75.5);
    expect(route.loadShare).toBeCloseTo(0.755);
    expect(route.stops[0].customerName).toBe('Customer 1');
    // 09:00 on the company's clock in Toronto, not the browser's
    expect(route.stops[0].arrival).toBe('2026-11-02T14:00:00.000Z');
    expect(route.routeId).toBe(1);
    expect(view.assignedCount).toBe(2);
    expect(view.lateCount).toBe(1);
    expect(view.unassigned).toEqual([{ orderId: 3, customerName: 'Customer 3', address: '3 Main St', weightKg: 10 }]);
  });

  it('separates vehicles the solver left unused', () => {
    const plan: Plan = { ...planBase, score: '0hard/0medium/0soft', unassignedOrderIds: [], routes: [{ routeId: 1, shiftId: 20, vehicleId: 7, geometry: null, stops: [] }] };
    const view = buildPlanView(plan, lookups, 'America/Toronto');
    expect(view.routes).toEqual([]);
    expect(view.idleRoutes.map((route) => route.vehicleName)).toEqual(['Van 7']);
  });

  it('keeps a route color tied to its shift, whatever order routes arrive in', () => {
    const shifts = [shift, { ...shift, id: 21 }];
    const a = { routeId: 1, shiftId: 20, vehicleId: 7, geometry: null, stops: [stop(1, 0)] };
    const b = { routeId: 2, shiftId: 21, vehicleId: 7, geometry: null, stops: [stop(2, 0)] };
    const first = buildPlanView({ ...planBase, score: '', unassignedOrderIds: [], routes: [a, b] }, { ...lookups, shifts }, 'America/Toronto');
    const second = buildPlanView({ ...planBase, score: '', unassignedOrderIds: [], routes: [b, a] }, { ...lookups, shifts }, 'America/Toronto');
    expect(first.routes[1].fill).toBe(ROUTE_COLORS[1].fill);
    expect(second.routes[1].fill).toBe(ROUTE_COLORS[1].fill);
  });

  it('falls back to what the plan itself says when an order is gone', () => {
    const plan: Plan = { ...planBase, score: '', unassignedOrderIds: [99], routes: [{ routeId: 1, shiftId: 20, vehicleId: 7, geometry: null, stops: [stop(99, 0)] }] };
    const view = buildPlanView(plan, lookups, 'America/Toronto');
    expect(view.routes[0].stops[0].customerName).toBe('Order #99');
    expect(view.routes[0].stops[0].address).toBe('raw');
    expect(view.routes[0].stops[0].latitude).toBeNull();
    expect(view.unassigned[0].customerName).toBe('Order #99');
  });
});
