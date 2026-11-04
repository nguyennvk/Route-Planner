import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { timezoneOptions } from '../../core/timezones';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';

@Component({
  selector: 'app-company-settings',
  imports: [ReactiveFormsModule, Field],
  template: `
    <div class="page">
      <div class="page-head"><div><h1>Company settings</h1><p>Shifts, delivery windows and day filters all use this timezone.</p></div></div>
      <form class="card card-pad settings" [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (runner.error()) { <div class="banner banner-error" role="alert">{{ runner.error() }}</div> }
        @if (saved()) { <div class="banner banner-ok" role="status">Saved.</div> }
        <app-field label="Company name" [control]="form.controls.name"><input class="input" formControlName="name" /></app-field>
        <app-field label="Timezone" [control]="form.controls.timezone">
          <select class="input" formControlName="timezone">
            @for (zone of zones; track zone) { <option [value]="zone">{{ zone }}</option> }
          </select>
        </app-field>
        <div class="form-actions"><button class="btn btn-primary" [disabled]="runner.busy()">{{ runner.busy() ? 'Saving…' : 'Save changes' }}</button></div>
      </form>
    </div>
  `,
  styles: `.settings { display: flex; flex-direction: column; gap: 14px; max-width: 520px; }`,
})
export class CompanySettings {
  private readonly api = inject(Api);
  private readonly ctx = inject(CompanyContext);

  protected readonly runner = new FormRunner();
  protected readonly saved = signal(false);
  protected readonly zones = timezoneOptions(this.ctx.zone());
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.ctx.company()!.name, [Validators.required, Validators.maxLength(254)]],
    timezone: [this.ctx.company()!.timezone, Validators.required],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saved.set(false);
    this.runner.run(this.api.updateCompany(this.ctx.id(), this.form.getRawValue()), (company) => {
      this.ctx.company.set(company);
      this.saved.set(true);
    }, this.form);
  }
}
