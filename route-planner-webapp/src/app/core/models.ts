// TypeScript mirrors of the API's DTO records (feature/**/dto/*.java).
// Instant and LocalDateTime both arrive as ISO strings.

export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: { field: string; message: string }[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

// The API really does spell this one "fullname"; every response says "fullName"
export interface RegisterRequest {
  email: string;
  password: string;
  fullname: string;
  phone: string;
}

export interface Me {
  id: number;
  email: string;
  fullName: string;
  phone: string;
  createdAt: string;
}

export interface Company {
  id: number;
  name: string;
  timezone: string;
  createdAt: string;
}

export type MemberRole = 'owner' | 'dispatcher' | 'driver';
export const MEMBER_ROLES: MemberRole[] = ['owner', 'dispatcher', 'driver'];

export interface Member {
  userId: number;
  fullName: string;
  email: string;
  role: MemberRole;
  active: boolean;
  joinedAt: string;
}

export interface Address {
  id: number;
  rawAddress: string;
  normalizedAddress: string | null;
  latitude: number;
  longitude: number;
  geocodeConfidence: number | null;
  geocodeSource: 'nominatim' | 'geocodio' | 'google' | 'manual_pin';
  createdAt: string;
}

export interface AddressSuggestion {
  formattedAddress: string;
  latitude: number;
  longitude: number;
  precise: boolean;
  token: string | null;
  geocodeSource: string;
}

// Either a signed suggestion or a manual pin, as CreateAddressRequest.java describes
export type NewAddress =
  | { token: string }
  | { rawAddress: string; latitude: number; longitude: number };

export interface Depot {
  id: number;
  name: string;
  address: Address;
}

export type VehicleType = 'motorbike' | 'car' | 'van' | 'truck';
export const VEHICLE_TYPES: VehicleType[] = ['motorbike', 'car', 'van', 'truck'];

export interface Vehicle {
  id: number;
  name: string;
  type: VehicleType;
  capacityKg: number;
  capacityM3: number | null;
  homeDepotId: number;
  active: boolean;
}

export interface VehicleRequest {
  name: string;
  type: VehicleType;
  capacityKg: number;
  homeDepotId: number;
  capacityM3?: number | null;
}

export interface Shift {
  id: number;
  vehicleId: number;
  vehicleName: string;
  driverUserId: number | null;
  driverName: string | null;
  startAt: string;
  endAt: string;
}

export interface ShiftRequest {
  vehicleId: number;
  startAt: string;
  endAt: string;
  driverUserId?: number | null;
}

export interface Customer {
  id: number;
  name: string;
  phone: string | null;
  email: string | null;
  defaultAddress: Address | null;
  createdAt: string;
}

export interface CustomerRequest {
  name?: string;
  phone?: string;
  email?: string;
  defaultAddressId?: number;
}

export interface Item {
  id: number;
  sku: string | null;
  name: string;
  weightKg: number;
  volumeM3: number | null;
}

export interface ItemRequest {
  name?: string;
  sku?: string;
  weightKg?: number;
  volumeM3?: number;
}

export type OrderStatus = 'pending' | 'planned' | 'out_for_delivery' | 'delivered' | 'failed' | 'cancelled';
export const ORDER_STATUSES: OrderStatus[] = ['pending', 'planned', 'out_for_delivery', 'delivered', 'failed', 'cancelled'];

export interface OrderLine {
  itemId: number;
  name: string;
  quantity: number;
  weightKg: number;
}

export interface Order {
  id: number;
  customer: Customer;
  address: Address;
  items: OrderLine[];
  totalWeightKg: number;
  status: OrderStatus;
  windowStart: string | null;
  windowEnd: string | null;
  windowStrict: boolean;
  serviceMinutes: number;
  notes: string | null;
  createdAt: string;
}

export interface OrderRequest {
  customerId?: number;
  items?: { itemId: number; quantity: number }[];
  addressId?: number;
  windowStart?: string;
  windowEnd?: string;
  windowStrict?: boolean;
  serviceMinutes?: number;
  notes?: string;
}

export interface PlanRequest {
  planDate: string;
  orderIds: number[];
  shiftIds: number[];
}

export type PlanStatus = 'draft' | 'solving' | 'published' | 'completed' | 'failed';

// Times are LocalDateTime on the company's wall clock (ZonedTime.toLocalDateTime in the API), with no offset
export interface PlanStop {
  stopId: number;
  sequence: number;
  orderId: number;
  name: string;               // the order's raw address
  arrivalTime: string | null;
  departureTime: string | null;
  minutesLate: number;
}

// RouteResponse.java: what a plan holds per vehicle, and what GET /drivers/me/routes lists for a driver
export interface PlanRoute {
  routeId: number;
  shiftId: number;
  vehicleId: number;
  stops: PlanStop[];
  // OSRM encoded polyline (precision 5) of the road path, depot to depot; null when OSRM couldn't route it
  geometry: string | null;
}

export interface Plan {
  planId: number;
  name: string;
  status: PlanStatus;
  planDate: string;
  score: string | null;       // null until solved
  routes: PlanRoute[];
  unassignedOrderIds: number[];   // only known right after solving; publishing returns an empty list
}
