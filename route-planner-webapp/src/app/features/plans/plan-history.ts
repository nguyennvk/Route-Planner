import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { PlanStatus, PlanSummary } from '../../core/models';
import { companyLocalToInstant, formatDateTime, formatDay } from '../../core/time';
import { errorMessage } from '../../core/errors';
import { Confirm } from '../../shared/confirm';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { Pager } from '../../shared/pager';
import { parseScore, softCostLabel } from '../planning/plan-view';

const STATUS: Record<PlanStatus, { label: string; badge: string }> = {
  draft: { label: 'Draft', badge: 'badge-warn' },
  published: { label: 'Published', badge: 'badge-ok' },
  completed: { label: 'Completed', badge: 'badge-info' },
  solving: { label: 'Solving', badge: '' },
  failed: { label: 'Failed', badge: 'badge-danger' },
};

// Every plan the company has made, newest day first. Opening one shows it on the Plan routes page for its day
@Component({
  selector: 'app-plan-history',
  imports: [RouterLink, Confirm, Icon, LoadState, Pager],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Plan history</h1><p>Every plan you've solved, newest first. A day can have several: one you published, and drafts solved before or after it.</p></div>
        <div class="page-actions"><a class="btn btn-primary" [routerLink]="ctx.link('plan')"><app-icon name="route" /> Plan routes</a></div>
      </div>

      <div class="toolbar" role="search" aria-label="Filter plans">
        <label class="field">
          <span class="field-label">Status</span>
          <select class="input" (change)="statusFilter.set($any($event.target).value || null); page.set(0)">
            <option value="" [selected]="statusFilter() === null">All statuses</option>
            @for (option of statusOptions; track option) {
              <option [value]="option" [selected]="statusFilter() === option">{{ status[option].label }}</option>
            }
          </select>
        </label>
        <label class="field">
          <span class="field-label">From</span>
          <input class="input" type="date" [value]="from()" (change)="from.set($any($event.target).value); page.set(0)" />
        </label>
        <label class="field">
          <span class="field-label">To</span>
          <input class="input" type="date" [value]="to()" (change)="to.set($any($event.target).value); page.set(0)" />
        </label>
        @if (filtered()) {
          <button type="button" class="btn btn-quiet" (click)="clearFilters()">Clear filters</button>
        }
      </div>

      @if (rangeError()) {
        <div class="banner banner-warn" role="alert"><app-icon name="warning" /><div>{{ rangeError() }}</div></div>
      } @else if (plans.hasValue()) {
        @if (plans.value().content.length === 0) {
          <div class="card empty">
            @if (filtered()) {
              <h2>No plans match these filters</h2>
              <p>Try another status or a wider range of days.</p>
              <button type="button" class="btn" (click)="clearFilters()">Clear filters</button>
            } @else {
              <h2>No plans yet</h2>
              <p>Plans you solve on the Plan routes page are kept here.</p>
            }
          </div>
        } @else {
          <div class="table-wrap"><table class="data">
            <thead><tr>
              <th>Day</th><th>Plan</th><th>Status</th><th class="num">Routes</th><th class="num">Stops</th>
              <th class="num">Estimated cost</th><th>Solved</th><th>Published</th><th><span class="visually-hidden">Actions</span></th>
            </tr></thead>
            <tbody>
              @for (plan of plans.value().content; track plan.planId) {
                <tr>
                  <td class="nowrap">{{ day(plan.planDate) }}</td>
                  <td class="mono">#{{ plan.planId }}<span class="cell-sub">by {{ plan.createdByName }}</span></td>
                  <td><span class="badge {{ status[plan.status].badge }}">{{ status[plan.status].label }}</span></td>
                  <td class="num">{{ plan.routeCount }}</td>
                  <td class="num">{{ plan.stopCount }}</td>
                  <td class="num nowrap">{{ cost(plan) }}</td>
                  <td class="nowrap">{{ when(plan.solvedAt) }}</td>
                  <td class="nowrap">{{ when(plan.publishedAt) }}</td>
                  <td class="row-actions">
                    @if (plan.routeCount > 0) {
                      <button type="button" class="btn btn-small" (click)="open(plan)">Open</button>
                    }
                    @if (ctx.isOwner() && deletable(plan)) {
                      <button type="button" class="btn btn-small btn-danger" [attr.aria-label]="'Delete plan #' + plan.planId"
                              (click)="openDelete(plan)"><app-icon name="trash" /></button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table></div>
          <app-pager [page]="page()" [totalPages]="plans.value().totalPages" [total]="plans.value().totalElements" (pageChange)="page.set($event)" />
        }
      } @else {
        <app-load-state [error]="plans.error()" (retry)="plans.reload()" />
      }
    </div>

    <app-confirm
      [open]="deleteTarget() !== null"
      [heading]="'Delete plan #' + (deleteTarget()?.planId ?? '') + '?'"
      message="Its routes are removed. Its orders stay pending and its shifts stay as they are. This can't be undone."
      action="Delete plan"
      [busy]="deleting()"
      [error]="deleteError()"
      (confirmed)="deletePlan()"
      (cancelled)="deleteTarget.set(null)" />
  `,
})
export class PlanHistory {
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly ctx = inject(CompanyContext);

  protected readonly status = STATUS;
  protected readonly statusOptions: PlanStatus[] = ['draft', 'published', 'completed', 'failed'];
  protected readonly page = signal(0);

  // Filters. Dates are the input's "YYYY-MM-DD", or '' when empty; the range includes both days
  protected readonly statusFilter = signal<PlanStatus | null>(null);
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly filtered = computed(() => this.statusFilter() !== null || !!this.from() || !!this.to());
  // Same-format date strings compare correctly as text
  protected readonly rangeError = computed(() =>
    this.from() && this.to() && this.to() < this.from() ? 'The "To" day is before the "From" day. Change one of them to see plans.' : null);

  // undefined params (a backwards range) keeps the request from going out at all
  protected readonly plans = rxResource({
    params: () => (this.rangeError() ? undefined : {
      id: this.ctx.id(), page: this.page(), status: this.statusFilter(), from: this.from() || null, to: this.to() || null,
    }),
    stream: ({ params: { id, page, status, from, to } }) => this.api.plans(id, { status, from, to, page, size: 20 }),
  });

  // Same rule as the API: a sent plan is history (orders, tracking links, claimed shifts point at it)
  protected deletable(plan: PlanSummary): boolean { return plan.status === 'draft' || plan.status === 'failed'; }

  protected readonly deleteTarget = signal<PlanSummary | null>(null);
  protected readonly deleting = signal(false);
  protected readonly deleteError = signal<string | null>(null);

  protected openDelete(plan: PlanSummary): void {
    this.deleteError.set(null);
    this.deleteTarget.set(plan);
  }

  protected deletePlan(): void {
    const plan = this.deleteTarget();
    if (!plan || this.deleting()) return;
    this.deleting.set(true);
    this.deleteError.set(null);
    this.api.deletePlan(plan.planId).subscribe({
      next: () => {
        this.deleting.set(false);
        this.deleteTarget.set(null);
        // The last row of a later page: step back so the page isn't empty
        if (this.plans.hasValue() && this.plans.value().content.length === 1 && this.page() > 0) this.page.update((p) => p - 1);
        else this.plans.reload();
      },
      error: (error: unknown) => {
        this.deleting.set(false);
        this.deleteError.set(errorMessage(error));
      },
    });
  }

  protected clearFilters(): void {
    this.statusFilter.set(null);
    this.from.set('');
    this.to.set('');
    this.page.set(0);
  }

  protected day(date: string): string { return formatDay(date); }

  // Times arrive on the company's wall clock; formatDateTime wants an instant
  protected when(local: string | null): string {
    const zone = this.ctx.zone();
    return formatDateTime(companyLocalToInstant(local, zone), zone);
  }

  // Plans solved before costs were in money have a soft score in seconds; this shows them as dollars anyway
  protected cost(plan: PlanSummary): string {
    const score = parseScore(plan.score);
    return score && plan.routeCount > 0 ? softCostLabel(score.soft) : '–';   // a plan with no routes cost nothing to run
  }

  // Plan routes shows the selected day, so switch to the plan's day first; ?plan= picks it among that day's plans
  protected open(plan: PlanSummary): void {
    this.ctx.date.set(plan.planDate);
    void this.router.navigate(this.ctx.link('plan'), { queryParams: { plan: plan.planId } });
  }
}
