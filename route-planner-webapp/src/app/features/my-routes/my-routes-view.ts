import { Address, Depot, PlanRoute, Vehicle } from '../../core/models';
import { companyLocalToInstant, dateInZone } from '../../core/time';
import { decodePath } from '../planning/plan-view';

export interface DriverStopView {
  stopId: number;
  sequence: number;
  address: string;
  latitude: number | null;
  longitude: number | null;
  arrival: string | null;
  departure: string | null;
  minutesLate: number;
}

export interface DriverRouteView {
  routeId: number;
  vehicleName: string;
  depot: Depot | null;
  date: string | null;            // the company's calendar day of the first stop
  firstArrival: string | null;
  lastDeparture: string | null;
  stops: DriverStopView[];
  path: [number, number][] | null;
  lateCount: number;
  unplacedCount: number;          // stops with no pin, listed but not on the map
}

export interface DriverLookups {
  vehicles: Vehicle[];
  depots: Depot[];
  addresses: Address[];
}

// GET /drivers/me/routes covers every company the driver works for, and a route carries ids and the raw
// address only: no company, customer or coordinates. Drivers can't read orders, but they can read the
// company's vehicles and addresses, so:
//   - a route belongs to this company when its vehicle is one of the company's
//   - a stop gets its pin from the company address with the same raw text
export function buildDriverRoutes(routes: PlanRoute[], { vehicles, depots, addresses }: DriverLookups, zone: string): DriverRouteView[] {
  const vehicleById = new Map(vehicles.map((vehicle) => [vehicle.id, vehicle]));
  const depotById = new Map(depots.map((depot) => [depot.id, depot]));
  const addressByRaw = new Map<string, Address>();
  for (const address of addresses) {
    if (!addressByRaw.has(address.rawAddress)) addressByRaw.set(address.rawAddress, address);
  }

  return routes.filter((route) => vehicleById.has(route.vehicleId)).map((route): DriverRouteView => {
    const vehicle = vehicleById.get(route.vehicleId)!;
    const stops = [...route.stops].sort((a, b) => a.sequence - b.sequence).map((stop): DriverStopView => {
      const address = addressByRaw.get(stop.name);
      return {
        stopId: stop.stopId,
        sequence: stop.sequence,
        address: address?.normalizedAddress ?? stop.name,
        latitude: address?.latitude ?? null,
        longitude: address?.longitude ?? null,
        arrival: companyLocalToInstant(stop.arrivalTime, zone),
        departure: companyLocalToInstant(stop.departureTime, zone),
        minutesLate: stop.minutesLate,
      };
    });
    const firstArrival = stops[0]?.arrival ?? null;
    return {
      routeId: route.routeId,
      vehicleName: vehicle.name,
      depot: depotById.get(vehicle.homeDepotId) ?? null,
      date: firstArrival ? dateInZone(firstArrival, zone) : null,
      firstArrival,
      lastDeparture: stops.at(-1)?.departure ?? null,
      stops,
      path: route.geometry ? decodePath(route.geometry) : null,
      lateCount: stops.filter((stop) => stop.minutesLate > 0).length,
      unplacedCount: stops.filter((stop) => stop.latitude === null).length,
    };
  });
}
