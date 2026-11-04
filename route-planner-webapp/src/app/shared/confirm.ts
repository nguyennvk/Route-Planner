import { Component, input, output } from '@angular/core';
import { Modal } from './modal';

@Component({
  selector: 'app-confirm',
  imports: [Modal],
  template: `
    <app-modal [open]="open()" [heading]="heading()" (closed)="cancelled.emit()">
      <p class="muted">{{ message() }}</p>
      @if (error()) { <div class="banner banner-error" role="alert" style="margin-top:12px">{{ error() }}</div> }
      <div class="form-actions">
        <button type="button" class="btn" (click)="cancelled.emit()">Keep it</button>
        <button type="button" class="btn btn-danger-solid" [disabled]="busy()" (click)="confirmed.emit()">{{ busy() ? 'Working…' : action() }}</button>
      </div>
    </app-modal>
  `,
})
export class Confirm {
  readonly open = input.required<boolean>();
  readonly heading = input.required<string>();
  readonly message = input.required<string>();
  readonly action = input.required<string>();
  readonly busy = input(false);
  readonly error = input<string | null>(null);
  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
}
