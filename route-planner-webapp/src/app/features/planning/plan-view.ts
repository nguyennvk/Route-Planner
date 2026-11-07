import { decode } from '@googlemaps/polyline-codec';
import { Depot, Order, Plan, Shift, Vehicle } from '../../core/models';
import { serverLocalToInstant } from '../../core/time';

// Fixed order, validated with the dataviz palette checker against the white surface. Three of these are
// under 3:1 contrast, so every route also has a name in the legend and numbered stop markers.
export const ROUTE_COLORS = [
  { fill: '#2a78d6', ink: '#ffffff' },
  { fill: '#eb6834', ink: '#101c18' },
  { fill: '#1baf7a', ink: '#101c18' },
  { fill: '#eda100', ink: '#101c18' },
  { fill: '#e87ba4', ink: '#101c18' },
  { fill: '#008300', ink: '#ffffff' },
  { fill: '#6250d6', ink: '#ffffff' },
  { fill: '#e34948', ink: '#101c18' },
];
const OTHER_ROUTE_COLOR = { fill: '#55645e', ink: '#ffffff' };

export interface ScoreView {
  hard: number;
  medium: number;
  soft: number;
  feasible: boolean;
}

// "0hard/0medium/-1037soft": see VehicleRoutingConstraintProvider for what each level counts
export function parseScore(score: string): ScoreView | null {
  const match = /(-?\d+)hard\/(-?\d+)medium\/(-?\d+)soft/.exec(score);
  if (!match) return null;
  const [hard, medium, soft] = match.slice(1).map(Number);
  return { hard, medium, soft, feasible: hard === 0 };
}

export interface StopView {
  sequence: number;
  orderId: number;
  customerName: string;
  address: string;
  latitude: number | null;
  longitude: number | null;
  weightKg: number | null;
  windowStart: string | null;
  windowEnd: string | null;
  windowStrict: boolean;
  arrival: string | null;
  departure: string | null;
  minutesLate: number;
}

export interface RouteView {
  shiftId: number;
  vehicleName: string;
  driverName: string | null;
  fill: string;
  ink: string;
  depot: Depot | null;
  stops: StopView[];
  usedKg: number;
  capacityKg: number | null;
  loadShare: number | null;
  path: [number, number][] | null;  // road path as [lng, lat], or null to join stops with straight lines
}

export interface PlanView {
  routes: RouteView[];
  idleRoutes: RouteView[];
  unassigned: { orderId: number; customerName: string; address: string; weightKg: number | null }[];
  score: ScoreView | null;
  assignedCount: number;
  lateCount: number;
}

export interface PlanLookups {
  orders: Order[];
  shifts: Shift[];
  vehicles: Vehicle[];
  depots: Depot[];
}

// The API returns ids and times only. This joins them with what the page has already loaded.
// A lookup can miss when a saved plan outlives an order or shift, so every field has a fallback.
export function buildPlanView(plan: Plan, { orders, shifts, vehicles, depots }: PlanLookups): PlanView {
  const orderById = new Map(orders.map((order) => [order.id, order]));
  const shiftById = new Map(shifts.map((shift) => [shift.id, shift]));
  const vehicleById = new Map(vehicles.map((vehicle) => [vehicle.id, vehicle]));
  const depotById = new Map(depots.map((depot) => [depot.id, depot]));

  // Color follows the shift, not the route's position in the response, so re-solving doesn't repaint vehicles
  const shiftOrder = [...plan.routes].map((route) => route.shiftId).sort((a, b) => a - b);

  const all = plan.routes.map((route): RouteView => {
    const vehicle = vehicleById.get(route.vehicleId);
    const shift = shiftById.get(route.shiftId);
    const color = ROUTE_COLORS[shiftOrder.indexOf(route.shiftId)] ?? OTHER_ROUTE_COLOR;
    const stops = route.stops.map((stop): StopView => {
      const order = orderById.get(stop.orderId);
      return {
        sequence: stop.sequence,
        orderId: stop.orderId,
        customerName: order?.customer.name ?? `Order #${stop.orderId}`,
        address: order?.address.normalizedAddress ?? order?.address.rawAddress ?? stop.name,
        latitude: order?.address.latitude ?? null,
        longitude: order?.address.longitude ?? null,
        weightKg: order?.totalWeightKg ?? null,
        windowStart: order?.windowStart ?? null,
        windowEnd: order?.windowEnd ?? null,
        windowStrict: order?.windowStrict ?? false,
        arrival: serverLocalToInstant(stop.arrivalTime),
        departure: serverLocalToInstant(stop.departureTime),
        minutesLate: stop.minutesLate,
      };
    });
    const usedKg = stops.reduce((sum, stop) => sum + (stop.weightKg ?? 0), 0);
    const capacityKg = vehicle?.capacityKg ?? null;
    return {
      shiftId: route.shiftId,
      vehicleName: vehicle?.name ?? shift?.vehicleName ?? `Vehicle #${route.vehicleId}`,
      driverName: shift?.driverName ?? null,
      fill: color.fill,
      ink: color.ink,
      depot: vehicle ? depotById.get(vehicle.homeDepotId) ?? null : null,
      stops,
      usedKg,
      capacityKg,
      loadShare: capacityKg ? usedKg / capacityKg : null,
      path: route.geometry ? decodePath(route.geometry) : null,
    };
  });

  const routes = all.filter((route) => route.stops.length > 0).sort((a, b) => a.shiftId - b.shiftId);
  return {
    routes,
    idleRoutes: all.filter((route) => route.stops.length === 0),
    unassigned: plan.unassignedOrderIds.map((orderId) => {
      const order = orderById.get(orderId);
      return {
        orderId,
        customerName: order?.customer.name ?? `Order #${orderId}`,
        address: order?.address.normalizedAddress ?? order?.address.rawAddress ?? '',
        weightKg: order?.totalWeightKg ?? null,
      };
    }),
    score: parseScore(plan.score),
    assignedCount: routes.reduce((sum, route) => sum + route.stops.length, 0),
    lateCount: routes.reduce((sum, route) => sum + route.stops.filter((stop) => stop.minutesLate > 0).length, 0),
  };
}

// OSRM sends geometries=polyline, which is precision 5. The decoder returns [lat, lng] and the map wants [lng, lat].
// Decoded here, inside the view's computed, so hovering a route doesn't decode every path again.
export function decodePath(polyline: string): [number, number][] {
  return decode(polyline, 5).map(([lat, lng]): [number, number] => [lng, lat]);
}

// Soft score is driving seconds plus lateness at 5 minutes of driving per late minute, so it reads as time
export function softCostLabel(soft: number): string {
  const totalMinutes = Math.round(Math.abs(soft) / 60);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours} h ${minutes} min` : `${minutes} min`;
}
