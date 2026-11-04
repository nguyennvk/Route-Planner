import { Component, computed, inject, linkedSignal, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Session } from '../../core/session';
import { signupRole } from '../../core/signup-role';
import { timezoneOptions } from '../../core/timezones';
import { Brand } from '../../shared/brand';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';

@Component({
  selector: 'app-company-list',
  imports: [ReactiveFormsModule, RouterLink, Brand, Field, Icon, LoadState],
  template: `
    <header class="top">
      <a routerLink="/" class="home"><app-brand /></a>
      <button type="button" class="btn btn-quiet" (click)="logout()"><app-icon name="logout" /> Log out</button>
    </header>
    <main class="wrap">
      @if (companies.hasValue() && me.hasValue()) {
        @if (showWaiting()) {
          <h1>You're signed up</h1>
          <p class="muted lead">To join a team, ask your company's owner to add you in Route Scheduler. They'll need this email:</p>
          <div class="card card-pad email">
            <span class="mono">{{ me.value().email }}</span>
            <button type="button" class="btn btn-small" (click)="copyEmail(me.value().email)">{{ copied() ? 'Copied' : 'Copy' }}</button>
          </div>
          <div class="waiting-actions">
            <button type="button" class="btn btn-primary" [disabled]="companies.isLoading()" (click)="checkAgain()">{{ companies.isLoading() ? 'Checking…' : 'Check again' }}</button>
            @if (checked() && !companies.isLoading()) { <span class="small muted" role="status">No company has added you yet.</span> }
          </div>
          <p class="switch-path muted">Running your own deliveries? <button type="button" class="link-btn" (click)="noCompanyView.set('create')">Set up a company</button></p>
        } @else if (hasCompanies() && !creating()) {
          <div class="head">
            <h1>Your companies</h1>
            <button type="button" class="btn" (click)="creating.set(true)"><app-icon name="plus" /> New company</button>
          </div>
          <ul class="list">
            @for (company of companies.value(); track company.id) {
              <li><a [routerLink]="['/app', company.id, 'dashboard']" class="card company">
                <span><strong>{{ company.name }}</strong><span class="small muted">{{ company.timezone }}</span></span>
                <app-icon name="arrow-right" />
              </a></li>
            }
          </ul>
        } @else {
          <h1>{{ hasCompanies() ? 'New company' : 'Set up your company' }}</h1>
          <p class="muted lead">Depots, vehicles, customers and orders all belong to a company. All times show in its timezone.</p>
          <form class="card card-pad" [formGroup]="form" (ngSubmit)="create()" novalidate>
            @if (runner.error()) { <div class="banner banner-error" role="alert" style="margin-bottom:12px">{{ runner.error() }}</div> }
            <div class="stack">
              <app-field label="Company name" [control]="form.controls.name">
                <input class="input" formControlName="name" />
              </app-field>
              <app-field label="Timezone" [control]="form.controls.timezone">
                <select class="input" formControlName="timezone">
                  @for (zone of zones; track zone) { <option [value]="zone">{{ zone }}</option> }
                </select>
              </app-field>
            </div>
            <div class="form-actions">
              @if (hasCompanies()) { <button type="button" class="btn" (click)="creating.set(false)">Cancel</button> }
              <button class="btn btn-primary" [disabled]="runner.busy()">{{ runner.busy() ? 'Creating…' : 'Create company' }}</button>
            </div>
          </form>
          @if (!hasCompanies()) {
            <p class="switch-path muted">Driving for a company? <button type="button" class="link-btn" (click)="noCompanyView.set('waiting')">Join your company's team</button></p>
          }
        }
      } @else {
        <app-load-state [error]="companies.error() ?? me.error()" (retry)="companies.reload(); me.reload()" />
      }
    </main>
  `,
  styles: `
    .top { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; background: var(--surface); border-bottom: 1px solid var(--line); }
    .home { color: inherit; text-decoration: none; }
    .wrap { max-width: 620px; margin: 0 auto; padding: 40px 20px; }
    .head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
    h1 { font-size: 26px; font-weight: 800; letter-spacing: -0.01em; }
    .lead { margin: 6px 0 18px; }
    .list { display: flex; flex-direction: column; gap: 8px; }
    .company { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px 12px; color: inherit; text-decoration: none; transition: border-color 150ms; }
    .company:hover { border-color: var(--brand); }
    .company strong, .company .small { display: block; }
    .stack { display: flex; flex-direction: column; gap: 14px; }
    .email { display: flex; align-items: center; justify-content: space-between; gap: 12px; overflow-wrap: anywhere; }
    .waiting-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 16px; }
    .switch-path { margin-top: 20px; }
    .link-btn { padding: 0; border: 0; background: none; color: var(--brand); font-weight: 600; cursor: pointer; text-decoration: underline; text-underline-offset: 2px; }
  `,
})
export class CompanyList {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly session = inject(Session);

  protected readonly zones = timezoneOptions();
  protected readonly runner = new FormRunner();
  protected readonly creating = signal(false);
  protected readonly companies = rxResource({ stream: () => this.api.companies() });
  protected readonly me = rxResource({ stream: () => this.api.me() });
  protected readonly hasCompanies = computed(() => this.companies.hasValue() && this.companies.value().length > 0);

  // With no company yet, drivers wait to be added and everyone else sets one up. Either can switch.
  protected readonly noCompanyView = linkedSignal<'create' | 'waiting'>(() =>
    this.me.hasValue() && signupRole(this.me.value().id) === 'driver' ? 'waiting' : 'create');
  protected readonly showWaiting = computed(() => !this.hasCompanies() && this.noCompanyView() === 'waiting');
  protected readonly checked = signal(false);
  protected readonly copied = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(254)]],
    timezone: [Intl.DateTimeFormat().resolvedOptions().timeZone, Validators.required],
  });

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.runner.run(this.api.createCompany(this.form.getRawValue()), (company) => {
      void this.router.navigate(['/app', company.id, 'dashboard']);
    }, this.form);
  }

  protected checkAgain(): void {
    this.checked.set(true);
    this.companies.reload();
  }

  protected copyEmail(email: string): void {
    // The address stays on screen to select by hand if the browser refuses clipboard access
    navigator.clipboard.writeText(email).then(() => this.copied.set(true), () => undefined);
  }

  protected logout(): void {
    const refreshToken = this.session.refreshToken();
    if (refreshToken) this.api.logout(refreshToken).subscribe({ error: () => undefined });
    this.session.clear();
    void this.router.navigate(['/']);
  }
}
