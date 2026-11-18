import { describe, expect, it } from 'vitest';
import { Address, Depot, PlanRoute, Vehicle } from '../../core/models';
import { nextStopId } from '../../shared/stop-actions';
import { buildDriverRoutes } from './my-routes-view';

const address = (id: number, raw: string, normalized: string | null = null): Address => ({
  id, rawAddress: raw, normalizedAddress: normalized, latitude: 43 + id / 100, longitude: -79, geocodeConfidence: null, geocodeSource: 'geocodio', createdAt: '',
});
const vehicle: Vehicle = { id: 7, name: 'Van 7', type: 'van', capacityKg: 100, capacityM3: null, homeDepotId: 3, active: true };
const depot = { id: 3, name: 'Main', address: address(9, 'Depot St') } as Depot;
const stop = (stopId: number, sequence: number, name: string, arrival: string, minutesLate = 0) => ({
  stopId, sequence, orderId: stopId, name, arrivalTime: arrival, departureTime: arrival.replace(/:00:00$/, ':05:00'), minutesLate, status: 'pending' as const, failureReason: null,
});
const route = (routeId: number, vehicleId: number, stops: PlanRoute['stops']): PlanRoute => ({ routeId, shiftId: 1, vehicleId, stops, geometry: null, assignmentStatus: 'pending', rejectionReason: null, answerOpen: true, startedAt: null, finishedAt: null, shareToken: `t${routeId}` });

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

  it('counts the stops still waiting for an answer, which Finish route waits on', () => {
    const [view] = buildDriverRoutes([route(1, 7, [
      { ...stop(10, 0, '1 Main St', '2026-11-09T09:00:00'), status: 'delivered' },
      { ...stop(11, 1, '2 King St', '2026-11-09T10:00:00'), status: 'arrived' },
      { ...stop(12, 2, '2 King St', '2026-11-09T11:00:00'), status: 'failed', failureReason: 'No one home' },
      stop(13, 3, '1 Main St', '2026-11-09T12:00:00'),
      { ...stop(14, 4, '1 Main St', '2026-11-09T13:00:00'), status: 'skipped' },
    ])], lookups, 'America/Toronto');
    expect(view.openCount).toBe(2);   // the arrived one and the pending one
    expect(view.stops[2]).toMatchObject({ status: 'failed', failureReason: 'No one home' });
  });
});

describe('nextStopId', () => {
  const stops = [
    { stopId: 1, status: 'delivered' as const },
    { stopId: 2, status: 'skipped' as const },
    { stopId: 3, status: 'pending' as const },
    { stopId: 4, status: 'pending' as const },
  ];

  it('is the first stop in driving order with no answer yet', () => {
    expect(nextStopId(stops, true)).toBe(3);
  });
  it('stays on a stop the driver has arrived at but not answered', () => {
    expect(nextStopId([{ stopId: 5, status: 'arrived' }, { stopId: 6, status: 'pending' }], true)).toBe(5);
  });
  it('is null before the start, after the finish, or once every stop is answered', () => {
    expect(nextStopId(stops, false)).toBeNull();
    expect(nextStopId([{ stopId: 1, status: 'delivered' }], true)).toBeNull();
  });
});

describe('buildDriverRoutes next stop', () => {
  it('only points at a stop while the route is in progress', () => {
    const lookups = { vehicles: [vehicle], depots: [depot], addresses: [] };
    const stops = [stop(1, 0, 'A', '2026-11-02T09:00:00'), stop(2, 1, 'B', '2026-11-02T10:00:00')];
    const notStarted = buildDriverRoutes([route(1, 7, stops)], lookups, 'America/Toronto')[0];
    const started = buildDriverRoutes([{ ...route(1, 7, stops), startedAt: '2026-11-02T08:30:00' }], lookups, 'America/Toronto')[0];
    expect(notStarted.nextStopId).toBeNull();
    expect(started.nextStopId).toBe(1);
  });
});
