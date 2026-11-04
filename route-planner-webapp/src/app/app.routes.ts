import { Routes } from '@angular/router';
import { requireLogin, requireLogout } from './core/auth.guard';

// loadComponent splits each page into its own file that downloads on first visit,
// so someone reading the landing page never pays for the map or the planner.
export const routes: Routes = [
  { path: '', pathMatch: 'full', title: 'Route Scheduler: delivery routes from your orders and shifts', loadComponent: () => import('./features/landing/landing').then((m) => m.Landing) },
  { path: 'login', title: 'Log in · Route Scheduler', canActivate: [requireLogout], loadComponent: () => import('./features/auth/login').then((m) => m.Login) },
  { path: 'register', title: 'Create account · Route Scheduler', canActivate: [requireLogout], loadComponent: () => import('./features/auth/register').then((m) => m.Register) },
  { path: 'app', pathMatch: 'full', title: 'Companies · Route Scheduler', canActivate: [requireLogin], loadComponent: () => import('./features/companies/company-list').then((m) => m.CompanyList) },
  {
    path: 'app/:companyId',
    canActivate: [requireLogin],
    loadComponent: () => import('./features/shell/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', title: 'Dashboard · Route Scheduler', loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard) },
      { path: 'plan', title: 'Plan routes · Route Scheduler', loadComponent: () => import('./features/planning/planning').then((m) => m.Planning) },
      { path: 'orders', title: 'Orders · Route Scheduler', loadComponent: () => import('./features/orders/orders').then((m) => m.Orders) },
      { path: 'shifts', title: 'Shifts · Route Scheduler', loadComponent: () => import('./features/shifts/shifts').then((m) => m.Shifts) },
      { path: 'fleet', title: 'Fleet · Route Scheduler', loadComponent: () => import('./features/fleet/fleet').then((m) => m.Fleet) },
      { path: 'customers', title: 'Customers · Route Scheduler', loadComponent: () => import('./features/customers/customers').then((m) => m.Customers) },
      { path: 'items', title: 'Items · Route Scheduler', loadComponent: () => import('./features/items/items').then((m) => m.Items) },
      { path: 'addresses', title: 'Addresses · Route Scheduler', loadComponent: () => import('./features/addresses/addresses').then((m) => m.Addresses) },
      { path: 'team', title: 'Team · Route Scheduler', loadComponent: () => import('./features/team/team').then((m) => m.Team) },
      { path: 'settings', title: 'Company settings · Route Scheduler', loadComponent: () => import('./features/companies/company-settings').then((m) => m.CompanySettings) },
    ],
  },
  { path: '**', redirectTo: '' },
];
