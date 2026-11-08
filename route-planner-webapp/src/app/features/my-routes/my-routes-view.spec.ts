import { describe, expect, it } from 'vitest';
import { Address, Depot, PlanRoute, Vehicle } from '../../core/models';
import { buildDriverRoutes } from './my-routes-view';

const address = (id: number, raw: string, normalized: string | null = null): Address => ({
  id, rawAddress: raw, normalizedAddress: normalized, latitude: 43 + id / 100, longitude: -79, geocodeConfidence: null, geocodeSource: 'geocodio', createdAt: '',
});
const vehicle: Vehicle = { id: 7, name: 'Van 7', type: 'van', capacityKg: 100, capacityM3: null, homeDepotId: 3, active: true };
const depot = { id: 3, name: 'Main', address: address(9, 'Depot St') } as Depot;
const stop = (stopId: number, sequence: number, name: string, arrival: string, minutesLate = 0) => ({
  stopId, sequence, orderId: stopId, name, arrivalTime: arrival, departureTime: arrival.replace(/:00:00$/, ':05:00'), minutesLate,
});
const route = (routeId: number, vehicleId: number, stops: PlanRoute['stops']): PlanRoute => ({ routeId, shiftId: 1, vehicleId, stops, geometry: null });

describe('buildDriverRoutes', () => {
  const lookups = { vehicles: [vehicle], depots: [depot], addresses: [address(1, '1 Main St', '1 Main St, Toronto, ON'), address(2, '2 King St')] };

  it("keeps only this company's routes, told apart by vehicle", () => {
    const views = buildDriverRoutes([route(1, 7, []), route(2, 99, [])], lookups, 'America/Toronto');
    expect(views.map((view) => view.routeId)).toEqual([1]);
  });

  it('puts stops in order, with pins from matching addresses and times on the company clock', () => {
    const [view] = buildDriverRoutes([route(1, 7, [
      stop(11, 1, '2 King St', '2026-11-09T10:00:00', 4),
      stop(10, 0, '1 Main St', '2026-11-09T09:00:00'),
    ])], lookups, 'America/Toronto');

    expect(view.vehicleName).toBe('Van 7');
    expect(view.depot).toBe(depot);
    expect(view.date).toBe('2026-11-09');
    expect(view.stops.map((s) => s.stopId)).toEqual([10, 11]);
    expect(view.stops[0].address).toBe('1 Main St, Toronto, ON');
    expect(view.stops[0].latitude).toBeCloseTo(43.01);
    expect(view.firstArrival).toBe('2026-11-09T14:00:00.000Z');
    expect(view.lastDeparture).toBe('2026-11-09T15:05:00.000Z');
    expect(view.lateCount).toBe(1);
  });

  it('lists a stop with no matching address, without a pin', () => {
    const [view] = buildDriverRoutes([route(1, 7, [stop(10, 0, '5 Gone Rd', '2026-11-09T09:00:00')])], lookups, 'America/Toronto');
    expect(view.stops[0]).toMatchObject({ address: '5 Gone Rd', latitude: null, longitude: null });
    expect(view.unplacedCount).toBe(1);
  });
});
