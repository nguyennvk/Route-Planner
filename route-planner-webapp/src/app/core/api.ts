import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, map, reduce } from 'rxjs';
import {
  Address, AddressSuggestion, Company, Customer, CustomerRequest, Depot, Item, ItemRequest, LoginResponse,
  Me, Member, MemberRole, NewAddress, Order, OrderRequest, OrderStatus, PageResponse, Plan, PlanRequest,
  RegisterRequest, Shift, ShiftRequest, Vehicle, VehicleRequest, VehicleType,
} from './models';

const API = '/api/v1';
export const MAX_PAGE_SIZE = 200;

type Query = Record<string, string | number | boolean | null | undefined>;

// Drops unset filters so the URL only carries what the user actually chose
function params(query: Query): HttpParams {
  let result = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== null && value !== undefined && value !== '') {
      result = result.set(key, String(value));
    }
  }
  return result;
}

// One method per controller endpoint, the same role a Feign client would play in Spring.
// These return cold Observables: nothing is sent until something subscribes.
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  // auth
  login(body: { email: string; password: string }) { return this.http.post<LoginResponse>(`${API}/auth/login`, body); }
  register(body: RegisterRequest) { return this.http.post<{ id: number }>(`${API}/auth/register`, body); }
  refresh(refreshToken: string) { return this.http.post<LoginResponse>(`${API}/auth/refresh`, { refreshToken }); }
  logout(refreshToken: string) { return this.http.post<void>(`${API}/auth/logout`, { refreshToken }); }
  me() { return this.http.get<Me>(`${API}/auth/me`); }

  // companies
  companies() { return this.http.get<Company[]>(`${API}/companies`); }
  company(id: number) { return this.http.get<Company>(`${API}/companies/${id}`); }
  createCompany(body: { name: string; timezone: string }) { return this.http.post<Company>(`${API}/companies`, body); }
  updateCompany(id: number, body: { name?: string; timezone?: string }) { return this.http.patch<Company>(`${API}/companies/${id}`, body); }

  // members
  members(companyId: number, role?: MemberRole) {
    return this.http.get<Member[]>(`${API}/companies/${companyId}/members`, { params: params({ role }) });
  }
  addMember(companyId: number, body: { email: string; role: MemberRole }) { return this.http.post<Member>(`${API}/companies/${companyId}/members`, body); }
  updateMember(companyId: number, userId: number, body: { role?: MemberRole; active?: boolean }) { return this.http.patch<Member>(`${API}/companies/${companyId}/members/${userId}`, body); }
  removeMember(companyId: number, userId: number) { return this.http.delete<void>(`${API}/companies/${companyId}/members/${userId}`); }

  // addresses
  suggestAddresses(rawAddress: string) { return this.http.get<AddressSuggestion[]>(`${API}/addresses/suggestion`, { params: params({ rawAddress }) }); }
  addresses(companyId: number, query: { search?: string; page?: number; size?: number } = {}) {
    return this.http.get<PageResponse<Address>>(`${API}/companies/${companyId}/addresses`, { params: params(query) });
  }
  createAddress(companyId: number, body: NewAddress) { return this.http.post<Address>(`${API}/companies/${companyId}/address`, body); }
  movePin(addressId: number, body: { latitude: number; longitude: number }) { return this.http.patch<Address>(`${API}/addresses/${addressId}`, body); }

  // depots
  depots(companyId: number, query: { page?: number; size?: number } = {}) {
    return this.http.get<PageResponse<Depot>>(`${API}/companies/${companyId}/depots`, { params: params(query) });
  }
  createDepot(companyId: number, body: { name: string; address: NewAddress }) { return this.http.post<Depot>(`${API}/companies/${companyId}/depots`, body); }

  // vehicles
  vehicles(companyId: number, query: { active?: boolean | null; type?: VehicleType | null; page?: number; size?: number } = {}) {
    return this.http.get<PageResponse<Vehicle>>(`${API}/companies/${companyId}/vehicles`, { params: params(query) });
  }
  createVehicle(companyId: number, body: VehicleRequest) { return this.http.post<Vehicle>(`${API}/companies/${companyId}/vehicles`, body); }
  updateVehicle(id: number, body: Partial<VehicleRequest> & { active?: boolean }) { return this.http.patch<Vehicle>(`${API}/vehicles/${id}`, body); }
  deactivateVehicle(id: number) { return this.http.delete<void>(`${API}/vehicles/${id}`); }

  // shifts
  shifts(companyId: number, query: { date: string; driverUserId?: number | null; page?: number; size?: number }) {
    return this.http.get<PageResponse<Shift>>(`${API}/companies/${companyId}/shifts`, { params: params(query) });
  }
  createShift(companyId: number, body: ShiftRequest) { return this.http.post<Shift>(`${API}/companies/${companyId}/shifts`, body); }
  updateShift(id: number, body: Partial<ShiftRequest>) { return this.http.patch<Shift>(`${API}/shifts/${id}`, body); }
  deleteShift(id: number) { return this.http.delete<void>(`${API}/shifts/${id}`); }

  // customers
  customers(companyId: number, query: { search?: string; phone?: string; page?: number; size?: number } = {}) {
    return this.http.get<PageResponse<Customer>>(`${API}/companies/${companyId}/customers`, { params: params(query) });
  }
  createCustomer(companyId: number, body: CustomerRequest) { return this.http.post<Customer>(`${API}/companies/${companyId}/customers`, body); }
  updateCustomer(id: number, body: CustomerRequest) { return this.http.patch<Customer>(`${API}/customers/${id}`, body); }

  // items
  items(companyId: number, query: { page?: number; size?: number } = {}) {
    return this.http.get<PageResponse<Item>>(`${API}/companies/${companyId}/items`, { params: params(query) });
  }
  createItem(companyId: number, body: ItemRequest) { return this.http.post<Item>(`${API}/companies/${companyId}/items`, body); }
  updateItem(id: number, body: ItemRequest) { return this.http.patch<Item>(`${API}/items/${id}`, body); }

  // orders
  orders(companyId: number, query: { date?: string | null; status?: OrderStatus | null; customerId?: number | null; page?: number; size?: number } = {}) {
    return this.http.get<PageResponse<Order>>(`${API}/companies/${companyId}/orders`, { params: params(query) });
  }
  createOrder(companyId: number, body: OrderRequest) { return this.http.post<Order>(`${API}/companies/${companyId}/orders`, body); }
  updateOrder(id: number, body: OrderRequest) { return this.http.patch<Order>(`${API}/orders/${id}`, body); }
  cancelOrder(id: number) { return this.http.post<Order>(`${API}/orders/${id}/cancel`, {}); }

  // planning: blocks until the solver stops (5 s without improvement, 30 s at most)
  solvePlan(companyId: number, body: PlanRequest) { return this.http.post<Plan>(`${API}/companies/${companyId}/plans`, body); }
}

// Follows totalPages for lists a dropdown or the planner needs in full (the API caps a page at 200)
export function fetchAll<T>(fetchPage: (page: number) => Observable<PageResponse<T>>): Observable<T[]> {
  return fetchPage(0).pipe(
    expand((res) => (res.page + 1 < res.totalPages ? fetchPage(res.page + 1) : EMPTY)),
    map((res) => res.content),
    reduce((all, content) => all.concat(content), [] as T[]),
  );
}
