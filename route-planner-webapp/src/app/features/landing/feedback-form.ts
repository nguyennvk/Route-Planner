import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api } from '../../core/api';
import { PriceBand } from '../../core/models';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';

const PRICE_OPTIONS: { value: PriceBand; label: string }[] = [
  { value: 'nothing', label: 'Nothing, it should be free' },
  { value: 'under_10', label: 'Under $10' },
  { value: 'from_10_to_20', label: '$10 – $20' },
  { value: 'from_20_to_50', label: '$20 – $50' },
  { value: 'from_50_to_100', label: '$50 – $100' },
  { value: 'not_sure', label: 'Not sure yet' },
];

// The landing page's feedback form. Works without an account; the API saves it and emails it to the owner
@Component({
  selector: 'app-feedback-form',
  imports: [ReactiveFormsModule, Field, Icon],
  template: `
    @if (sent()) {
      <div class="thanks" role="status">
        <app-icon name="check" [size]="28" />
        <h3>Thank you, that really helps.</h3>
        <p>We read every message. If you left your email, we may reply with a question or two.</p>
        <button type="button" class="btn" (click)="again()">Send another</button>
      </div>
    } @else {
      <form [formGroup]="form" (ngSubmit)="send()" novalidate>
        @if (runner.error()) { <div class="banner banner-error" role="alert">{{ runner.error() }}</div> }

        <app-field label="What would make Route Scheduler useful for you?" [control]="form.controls.message"
                   hint="What you deliver, what's hard about planning it today, or what's missing here.">
          <textarea class="input" rows="5" maxlength="4000" formControlName="message"
                    placeholder="For example: we run 6 vans in the city and plan by hand every morning…"></textarea>
        </app-field>

        <fieldset class="price">
          <legend class="field-label">What would you pay for it per month?</legend>
          <div class="price-options">
            @for (option of priceOptions; track option.value) {
              <label class="price-option" [class.chosen]="form.controls.priceBand.value === option.value">
                <input type="radio" formControlName="priceBand" [value]="option.value" />
                <span>{{ option.label }}</span>
              </label>
            }
          </div>
          @if (form.controls.priceBand.touched && form.controls.priceBand.invalid) {
            <span class="field-error" role="alert">Pick one, even "Not sure yet"</span>
          }
        </fieldset>

        <div class="row">
          <app-field label="An exact amount, $ per month" [optional]="true" [control]="form.controls.priceAmount">
            <input class="input" type="number" min="0" max="100000" step="1" inputmode="decimal" formControlName="priceAmount" />
          </app-field>
          <app-field label="Your name" [optional]="true" [control]="form.controls.name">
            <input class="input" maxlength="100" autocomplete="name" formControlName="name" />
          </app-field>
          <app-field label="Email" [optional]="true" hint="Only so we can reply" [control]="form.controls.email">
            <input class="input" type="email" maxlength="254" autocomplete="email" formControlName="email" />
          </app-field>
        </div>

        <!-- Honeypot: hidden from people and screen readers, so only bots fill it in -->
        <input class="trap" type="text" tabindex="-1" autocomplete="off" aria-hidden="true" formControlName="website" />

        <div class="actions">
          <button class="btn btn-primary btn-large" [disabled]="runner.busy()">{{ runner.busy() ? 'Sending…' : 'Send feedback' }}</button>
        </div>
      </form>
    }
  `,
  styles: `
    form { display: flex; flex-direction: column; gap: 18px; }
    .price { margin: 0; padding: 0; border: 0; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
    .price-options { display: flex; flex-wrap: wrap; gap: 8px; }
    .price-option { display: inline-flex; align-items: center; gap: 8px; padding: 9px 14px 7px; border: 1px solid var(--line-strong); border-radius: 999px; background: var(--surface); cursor: pointer; font-weight: 500; }
    .price-option input { margin: 0; accent-color: var(--brand); }
    .price-option.chosen { border-color: var(--brand); background: var(--brand-soft); color: var(--sign-green-800); }
    .row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
    @media (max-width: 760px) { .row { grid-template-columns: minmax(0, 1fr); } }
    .trap { position: absolute; left: -10000px; width: 1px; height: 1px; opacity: 0; }
    .actions { display: flex; justify-content: flex-start; }
    .thanks { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; }
    .thanks app-icon { color: var(--brand); }
    .thanks h3 { font-size: 22px; }
    .thanks p { color: var(--ink-soft); }
  `,
})
export class FeedbackForm {
  private readonly api = inject(Api);

  protected readonly priceOptions = PRICE_OPTIONS;
  protected readonly runner = new FormRunner();
  protected readonly sent = signal(false);
  protected readonly form = inject(FormBuilder).group({
    message: ['', [Validators.required, Validators.maxLength(4000)]],
    priceBand: [null as PriceBand | null, Validators.required],
    priceAmount: [null as number | null, [Validators.min(0), Validators.max(100000)]],
    name: ['', Validators.maxLength(100)],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    website: [''],
  });

  protected send(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.runner.run(this.api.sendFeedback({
      message: value.message!.trim(),
      priceBand: value.priceBand!,
      priceAmount: value.priceAmount === null || value.priceAmount === undefined ? null : Number(value.priceAmount),
      name: value.name?.trim() || null,
      email: value.email?.trim() || null,
      website: value.website ?? '',
    }), () => this.sent.set(true), this.form);
  }

  protected again(): void {
    this.form.reset({ message: '', priceBand: null, priceAmount: null, name: '', email: '', website: '' });
    this.runner.reset();
    this.sent.set(false);
  }
}
