import { Component, computed, input, linkedSignal, output } from '@angular/core';
import { AssignmentStatus } from '../core/models';
import { Icon } from './icon';

export const REJECT_REASON_MAX = 500;   // DriverRejectRequest's @Size

// Where the driver is with a route, in the order the API allows: answer, start, finish
type Stage = 'pending' | 'accepted' | 'rejected' | 'started' | 'finished' | 'locked';

// The driver's panel for one route, used on My routes and on the no-login share page: accept or turn down
// (changeable until the route starts), then start and finish. It only shows state and emits the choice;
// each page makes its own API call, since one is logged in and one isn't.
@Component({
  selector: 'app-route-actions',
  imports: [Icon],
  template: `
    <section class="card card-pad panel" [class]="'is-' + stage()" aria-labelledby="actions-heading">
      @if (form() === 'decline') {
        <form class="stack" (submit)="$event.preventDefault(); sendReject()">
          <h2 id="actions-heading">{{ status() === 'accepted' ? "Can't drive it after all?" : 'Turn down this route' }}</h2>
          <label class="field">
            <span class="field-label">Reason <span class="muted">(optional)</span></span>
            <textarea class="input" rows="3" [maxLength]="maxReason" [value]="reason()" (input)="reason.set($any($event.target).value)"
                      placeholder="For example: off sick, van not available"></textarea>
            <span class="field-hint">The owner sees this, so they can find another driver.</span>
          </label>
          <div class="actions">
            <button type="button" class="btn btn-quiet" [disabled]="busy()" (click)="form.set(null)">Back</button>
            <button type="submit" class="btn btn-danger-solid" [disabled]="busy()">{{ busy() ? 'Sending…' : 'Turn down route' }}</button>
          </div>
        </form>
      } @else if (form() === 'finish') {
        <div class="text">
          <h2 id="actions-heading">Finish this route?</h2>
          <p class="small muted">Do this once you're back at the depot. It can't be undone.</p>
        </div>
        <div class="actions">
          <button type="button" class="btn btn-quiet" [disabled]="busy()" (click)="form.set(null)">Not yet</button>
          <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="finish.emit()">{{ busy() ? 'Sending…' : 'Finish route' }}</button>
        </div>
      } @else {
        <div class="text">
          @switch (stage()) {
            @case ('pending') {
              <h2 id="actions-heading">Can you drive this route?</h2>
              <p class="small muted">The owner is waiting for your answer. You can change it until you start the route.</p>
            }
            @case ('accepted') {
              <h2 id="actions-heading"><app-icon name="check" /> You accepted this route</h2>
              <p class="small muted">Start it when you leave the depot. After that your answer can't change.</p>
            }
            @case ('rejected') {
              <h2 id="actions-heading"><app-icon name="warning" /> You turned this route down</h2>
              @if (rejectionReason()) { <p class="small reason">Your reason: “{{ rejectionReason() }}”</p> }
              <p class="small muted">You can change your answer until the route starts.</p>
            }
            @case ('started') {
              <h2 id="actions-heading"><app-icon name="navigation" /> Route in progress</h2>
              <p class="small muted">Started at <span class="mono">{{ clock(startedAt()) }}</span>.</p>
            }
            @case ('finished') {
              <h2 id="actions-heading"><app-icon name="check" /> Route finished</h2>
              <p class="small muted">Started <span class="mono">{{ clock(startedAt()) }}</span>, finished <span class="mono">{{ clock(finishedAt()) }}</span>.</p>
            }
            @case ('locked') {
              <h2 id="actions-heading">{{ status() === 'rejected' ? 'You turned this route down' : 'This plan is completed' }}</h2>
              <p class="small muted">The plan is completed, so this route can't change anymore.</p>
            }
          }
        </div>
        <div class="actions">
          @switch (stage()) {
            @case ('pending') {
              <button type="button" class="btn" [disabled]="busy()" (click)="form.set('decline')">I can't take it</button>
              <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="accept.emit()"><app-icon name="check" /> {{ busy() ? 'Sending…' : 'Accept route' }}</button>
            }
            @case ('accepted') {
              <button type="button" class="btn" [disabled]="busy()" (click)="form.set('decline')">I can't take it</button>
              <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="start.emit()"><app-icon name="navigation" /> {{ busy() ? 'Starting…' : 'Start route' }}</button>
            }
            @case ('rejected') {
              <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="accept.emit()"><app-icon name="check" /> {{ busy() ? 'Sending…' : 'Accept after all' }}</button>
            }
            @case ('started') {
              <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="form.set('finish')">Finish route</button>
            }
          }
        </div>
      }
      @if (error(); as message) {
        <p class="banner banner-error" role="alert"><app-icon name="alert" /> {{ message }}</p>
      }
    </section>
  `,
  styles: `
    .panel { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }
    .panel.is-pending { border-color: var(--sign-amber-strong); box-shadow: inset 4px 0 0 var(--sign-amber); }
    .panel.is-accepted, .panel.is-finished { border-color: #b5dccb; box-shadow: inset 4px 0 0 var(--brand); }
    .panel.is-rejected { border-color: #f2d78f; box-shadow: inset 4px 0 0 var(--sign-amber-strong); }
    .panel.is-started { border-color: #bcd6f2; box-shadow: inset 4px 0 0 var(--info-ink); }
    .panel h2 { display: flex; align-items: center; gap: 8px; font-size: 17px; font-weight: 700; }
    .is-accepted h2 app-icon, .is-finished h2 app-icon { color: var(--brand); }
    .is-rejected h2 app-icon { color: var(--warn-ink); }
    .is-started h2 app-icon { color: var(--info-ink); }
    .text { flex: 1 1 260px; min-width: 0; }
    .reason { overflow-wrap: anywhere; color: var(--ink-soft); }
    .actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
    .actions:empty { display: none; }
    .stack { flex: 1; display: flex; flex-direction: column; gap: 10px; }
    .panel .banner { flex-basis: 100%; }
  `,
})
export class RouteActions {
  // The page reuses this component when the driver picks another route; the half-typed reason must not follow
  readonly routeId = input.required<number>();
  readonly status = input.required<AssignmentStatus>();
  readonly rejectionReason = input<string | null>(null);
  readonly answerOpen = input(true);              // RouteResponse.answerOpen
  readonly startedAt = input<string | null>(null);   // company wall clock, e.g. "2026-11-04T08:12:30"
  readonly finishedAt = input<string | null>(null);
  readonly busy = input(false);
  readonly error = input<string | null>(null);
  readonly accept = output<void>();
  readonly reject = output<string | null>();
  readonly start = output<void>();
  readonly finish = output<void>();

  protected readonly stage = computed((): Stage => {
    if (this.finishedAt()) return 'finished';
    if (this.startedAt()) return 'started';
    if (!this.answerOpen()) return 'locked';      // not started, but the plan was completed
    return this.status();
  });

  protected readonly maxReason = REJECT_REASON_MAX;
  // Which inline step is open. Keyed on route and stage, so it closes once the action lands or on another route
  protected readonly form = linkedSignal<string, 'decline' | 'finish' | null>({
    source: () => `${this.routeId()}:${this.stage()}`,
    computation: () => null,
  });
  protected readonly reason = linkedSignal({ source: this.routeId, computation: () => '' });

  protected sendReject(): void {
    if (this.busy()) return;
    this.reject.emit(this.reason().trim() || null);
  }

  protected clock(local: string | null): string { return local ? local.slice(11, 16) : '–'; }
}

// 409: the route moved on meanwhile (the API's detail says how), or two requests raced.
// 404: unpublished or no longer theirs.
export function actionErrorMessage(status: number | undefined, fallback: string): string {
  if (status === 409) return `${fallback} Reload to see the latest.`;
  if (status === 404) return "This route isn't available anymore. The owner may have changed the plan.";
  return fallback;
}
