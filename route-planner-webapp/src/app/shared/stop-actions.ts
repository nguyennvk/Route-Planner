import { Component, input, linkedSignal, output } from '@angular/core';
import { StopStatus } from '../core/models';
import { Icon } from './icon';

export const FAIL_REASON_MAX = 500;   // StopFailRequest's @Size

// The stop the driver is heading to (or standing at): the first one in driving order with no answer yet.
// Only while the route is in progress; before the start and after the finish there's no "next".
// stops must already be sorted by sequence.
export function nextStopId(stops: readonly { stopId: number; status: StopStatus }[], inProgress: boolean): number | null {
  if (!inProgress) return null;
  return stops.find((stop) => stop.status === 'pending' || stop.status === 'arrived')?.stopId ?? null;
}

// Answered stops fade back so the open ones, and the next one most of all, stand out
export function isDone(status: StopStatus): boolean {
  return status === 'delivered' || status === 'failed' || status === 'skipped';
}

// Brings a stop's row to the middle of the screen; the row's id is stop-<stopId>
export function scrollToStop(stopId: number): void {
  document.getElementById(`stop-${stopId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

// One tap for the usual cases; "Other" opens a text box
const QUICK_REASONS = ['No one home', "Couldn't find the address", 'Customer refused it', "Couldn't get access"];

// The driver's answer for one stop, used on My routes and the share page. While the route is in progress an
// open stop offers Arrived, Delivered and Couldn't deliver; once answered it shows the result. Like RouteActions,
// it only emits the choice, and each page makes its own API call.
@Component({
  selector: 'app-stop-actions',
  imports: [Icon],
  template: `
    @switch (status()) {
      @case ('delivered') { <span class="badge badge-ok"><app-icon name="check" [size]="14" /> Delivered</span> }
      @case ('failed') {
        <span class="result">
          <span class="badge badge-danger">Not delivered</span>
          @if (failureReason()) { <span class="small muted reason">{{ failureReason() }}</span> }
        </span>
      }
      @case ('skipped') { <span class="badge">Skipped, order cancelled</span> }
      @default {
        @if (!active()) {
          @if (status() === 'arrived') { <span class="badge badge-info">Arrived</span> }
        } @else if (form()) {
          <form class="fail" (submit)="$event.preventDefault(); sendFail()">
            <span class="field-label">Why couldn't you deliver it?</span>
            <div class="chips">
              @for (option of quickReasons; track option) {
                <button type="button" class="btn btn-small" [class.chosen]="reason() === option" [disabled]="busy()" (click)="reason.set(option)">{{ option }}</button>
              }
            </div>
            <input class="input" [maxLength]="maxReason" placeholder="Or type the reason" aria-label="Reason"
                   [value]="reason()" (input)="reason.set($any($event.target).value)" />
            <div class="buttons">
              <button type="button" class="btn btn-quiet btn-small" [disabled]="busy()" (click)="form.set(false)">Back</button>
              <button type="submit" class="btn btn-danger-solid btn-small" [disabled]="busy() || !reason().trim()">{{ busy() ? 'Sending…' : 'Not delivered' }}</button>
            </div>
          </form>
        } @else {
          <div class="buttons">
            @if (status() === 'pending') {
              <button type="button" class="btn btn-small" [disabled]="busy()" (click)="arrive.emit()">Arrived</button>
            } @else {
              <span class="badge badge-info">Arrived</span>
            }
            <button type="button" class="btn btn-small btn-primary" [disabled]="busy()" (click)="deliver.emit()"><app-icon name="check" [size]="14" /> Delivered</button>
            <button type="button" class="btn btn-small" [disabled]="busy()" (click)="form.set(true)">Couldn't deliver</button>
          </div>
        }
      }
    }
  `,
  styles: `
    :host { display: block; }
    .buttons { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .badge { display: inline-flex; align-items: center; gap: 4px; }
    .result { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; }
    .reason { overflow-wrap: anywhere; }
    .fail { display: flex; flex-direction: column; gap: 8px; padding: 10px; border: 1px solid var(--line); border-radius: var(--radius-m); background: var(--surface); }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
    .chosen { border-color: var(--danger); color: var(--danger); font-weight: 600; }
    .fail .buttons { justify-content: flex-end; }
  `,
})
export class StopActions {
  readonly stopId = input.required<number>();
  readonly status = input.required<StopStatus>();
  readonly failureReason = input<string | null>(null);
  readonly active = input(false);       // the route is started and not finished
  readonly busy = input(false);
  readonly arrive = output<void>();
  readonly deliver = output<void>();
  readonly fail = output<string>();

  protected readonly quickReasons = QUICK_REASONS;
  protected readonly maxReason = FAIL_REASON_MAX;
  // Closes and clears once the answer lands (the status changes) or on another stop
  protected readonly form = linkedSignal<string, boolean>({ source: () => `${this.stopId()}:${this.status()}`, computation: () => false });
  protected readonly reason = linkedSignal({ source: () => `${this.stopId()}:${this.status()}`, computation: () => '' });

  protected sendFail(): void {
    const reason = this.reason().trim();
    if (reason && !this.busy()) this.fail.emit(reason);
  }
}
