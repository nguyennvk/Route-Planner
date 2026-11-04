import { Component, computed, inject, input, linkedSignal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map, switchMap } from 'rxjs';
import { Api } from '../../core/api';
import { Session } from '../../core/session';
import { SignupRole, rememberSignupRole } from '../../core/signup-role';
import { Brand } from '../../shared/brand';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon, IconName } from '../../shared/icon';
import { AuthArt } from './auth-art';

const ROLE_OPTIONS: { value: SignupRole; title: string; text: string; icon: IconName }[] = [
  { value: 'owner', title: 'I run deliveries', text: 'Set up your company, fleet and routes.', icon: 'building' },
  { value: 'driver', title: 'I drive for a company', text: "Join your company's team and see its shifts.", icon: 'truck' },
];

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, Brand, Field, Icon, AuthArt],
  template: `
    <div class="panel">
      <a routerLink="/" class="back"><app-brand /></a>
      <h1>Create your account</h1>
      <p class="lead">{{ lead() }}</p>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        @if (runner.error()) { <div class="banner banner-error" role="alert">{{ runner.error() }}</div> }

        <fieldset class="roles">
          <legend class="field-label">How will you use Route Scheduler?</legend>
          <div class="role-options">
            @for (option of roleOptions; track option.value) {
              <label class="role" [class.selected]="selectedRole() === option.value">
                <input type="radio" name="role" [value]="option.value" [checked]="selectedRole() === option.value" (change)="selectedRole.set(option.value)" />
                <app-icon [name]="option.icon" [size]="20" />
                <strong>{{ option.title }}</strong>
                <span class="small muted">{{ option.text }}</span>
              </label>
            }
          </div>
        </fieldset>

        <app-field label="Full name" [control]="form.controls.fullname">
          <input class="input" formControlName="fullname" autocomplete="name" />
        </app-field>
        <app-field label="Email" [control]="form.controls.email" [hint]="emailHint()">
          <input class="input" type="email" formControlName="email" autocomplete="email" />
        </app-field>
        <app-field label="Phone" [control]="form.controls.phone" hint="With country code, e.g. +14165550123">
          <input class="input" type="tel" formControlName="phone" autocomplete="tel" />
        </app-field>
        <app-field label="Password" [control]="form.controls.password" hint="8 to 72 characters">
          <input class="input" type="password" formControlName="password" autocomplete="new-password" />
        </app-field>
        <button class="btn btn-primary" [disabled]="runner.busy()">{{ runner.busy() ? 'Creating account…' : 'Create account' }}</button>
      </form>
      <p class="switch">Already registered? <a routerLink="/login">Log in</a></p>
    </div>
    <div class="art"><app-auth-art /><p>Every stop in the right order.</p></div>
  `,
  styleUrl: './auth-layout.css',
  styles: `
    .roles { margin: 0; padding: 0; border: 0; min-width: 0; }
    .roles legend { padding: 0; margin-bottom: 6px; }
    .role-options { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    .role { position: relative; display: flex; flex-direction: column; gap: 3px; padding: 12px 12px 10px; border: 1px solid var(--line-strong); border-radius: var(--radius-m); cursor: pointer; transition: border-color 150ms, background-color 150ms; }
    .role:hover { border-color: var(--gray-600); }
    .role.selected { border-color: var(--brand); background: var(--brand-soft); box-shadow: inset 0 0 0 1px var(--brand); }
    .role app-icon { margin-bottom: 3px; }
    .role strong { line-height: 1.2; }
    .role input { position: absolute; top: 12px; right: 12px; margin: 0; width: 16px; height: 16px; accent-color: var(--brand); }
    .role:has(input:focus-visible) { outline: 2px solid var(--brand); outline-offset: 2px; }
    @media (max-width: 400px) { .role-options { grid-template-columns: minmax(0, 1fr); } }
  `,
})
export class Register {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  // Filled from ?role=driver by withComponentInputBinding(), so a sign-up link can preselect it
  readonly role = input<string>();
  protected readonly selectedRole = linkedSignal<SignupRole>(() => (this.role() === 'driver' ? 'driver' : 'owner'));

  protected readonly roleOptions = ROLE_OPTIONS;
  protected readonly lead = computed(() => this.selectedRole() === 'driver'
    ? "Your company's owner adds you to their team using your email."
    : "Next you'll set up your company, then your fleet.");
  protected readonly emailHint = computed(() => this.selectedRole() === 'driver' ? "You'll give this to your company's owner" : '');

  protected readonly runner = new FormRunner();
  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullname: ['', Validators.required],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', Validators.required],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const registerThenLogin = this.api.register(value).pipe(
      switchMap((account) => this.api.login({ email: value.email, password: value.password }).pipe(
        map((tokens) => ({ account, tokens })),
      )),
    );
    this.runner.run(registerThenLogin, ({ account, tokens }) => {
      this.session.store(tokens);
      rememberSignupRole(account.id, this.selectedRole());
      void this.router.navigate(['/app']);
    }, this.form);
  }
}
