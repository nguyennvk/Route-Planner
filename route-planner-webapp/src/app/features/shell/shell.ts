import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, forkJoin, map, of, throwError } from 'rxjs';
import { Api } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { Session } from '../../core/session';
import { Brand } from '../../shared/brand';
import { Icon, IconName } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';

interface NavLink { path: string; label: string; icon: IconName; ownerOnly?: boolean; }

const NAV: { heading: string; links: NavLink[] }[] = [
  { heading: 'Plan', links: [
    { path: 'dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: 'plan', label: 'Plan routes', icon: 'route', ownerOnly: true },
  ] },
  { heading: 'Operations', links: [
    { path: 'orders', label: 'Orders', icon: 'package', ownerOnly: true },
    { path: 'shifts', label: 'Shifts', icon: 'clock' },
    { path: 'fleet', label: 'Fleet', icon: 'truck' },
  ] },
  { heading: 'Directory', links: [
    { path: 'customers', label: 'Customers', icon: 'users', ownerOnly: true },
    { path: 'items', label: 'Items', icon: 'boxes' },
    { path: 'addresses', label: 'Addresses', icon: 'pin' },
  ] },
  { heading: 'Company', links: [
    { path: 'team', label: 'Team', icon: 'user', ownerOnly: true },
    { path: 'settings', label: 'Settings', icon: 'settings', ownerOnly: true },
  ] },
];

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, Brand, LoadState],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
})
export class Shell {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly session = inject(Session);
  protected readonly ctx = inject(CompanyContext);

  // Filled from the :companyId route segment by withComponentInputBinding()
  readonly companyId = input.required<string>();
  private readonly id = computed(() => Number(this.companyId()));

  protected readonly menuOpen = signal(false);

  protected readonly companies = rxResource({ stream: () => this.api.companies() });
  protected readonly me = rxResource({ stream: () => this.api.me() });

  // The API has no "my role" endpoint, but only owners may list members, so a 403 means "not the owner"
  protected readonly current = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => forkJoin({
      company: this.api.company(id),
      isOwner: this.api.members(id, 'owner').pipe(
        map(() => true),
        catchError((error: unknown) => (error instanceof HttpErrorResponse && error.status === 403 ? of(false) : throwError(() => error))),
      ),
    }),
  });

  protected readonly ready = computed(() => this.ctx.company()?.id === this.id());

  protected readonly nav = computed(() =>
    NAV.map((group) => ({ ...group, links: group.links.filter((link) => this.ctx.isOwner() || !link.ownerOnly) }))
      .filter((group) => group.links.length > 0),
  );

  constructor() {
    effect(() => {
      if (this.current.hasValue()) {
        const { company, isOwner } = this.current.value();
        this.ctx.enter(company, isOwner);
      }
    });
  }

  protected switchCompany(value: string): void {
    this.menuOpen.set(false);
    void this.router.navigate(value === 'all' ? ['/app'] : ['/app', Number(value), 'dashboard']);
  }

  protected logout(): void {
    const refreshToken = this.session.refreshToken();
    if (refreshToken) {
      // Best effort: the server revokes the token, but leaving must work even if that call fails
      this.api.logout(refreshToken).subscribe({ error: () => undefined });
    }
    this.session.clear();
    void this.router.navigate(['/']);
  }
}
