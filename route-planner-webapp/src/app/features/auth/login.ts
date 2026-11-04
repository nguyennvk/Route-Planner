import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Session } from '../../core/session';
import { Brand } from '../../shared/brand';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { AuthArt } from './auth-art';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, Brand, Field, AuthArt],
  template: `
    <div class="panel">
      <a routerLink="/" class="back"><app-brand /></a>
      <h1>Log in</h1>
      <p class="lead">Pick up your delivery day where you left it.</p>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        @if (runner.error()) { <div class="banner banner-error" role="alert">{{ runner.error() }}</div> }
        <app-field label="Email" [control]="form.controls.email">
          <input class="input" type="email" formControlName="email" autocomplete="email" />
        </app-field>
        <app-field label="Password" [control]="form.controls.password">
          <input class="input" type="password" formControlName="password" autocomplete="current-password" />
        </app-field>
        <button class="btn btn-primary" [disabled]="runner.busy()">{{ runner.busy() ? 'Logging in…' : 'Log in' }}</button>
      </form>
      <p class="switch">New here? <a routerLink="/register">Create an account</a></p>
    </div>
    <div class="art"><app-auth-art /><p>Every order on a route.</p></div>
  `,
  styleUrl: './auth-layout.css',
})
export class Login {
  private readonly api = inject(Api);
  private readonly session = inject(Session);
  private readonly router = inject(Router);

  protected readonly runner = new FormRunner();
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.runner.run(this.api.login(this.form.getRawValue()), (tokens) => {
      this.session.store(tokens);
      void this.router.navigate(['/app']);
    }, this.form);
  }
}
