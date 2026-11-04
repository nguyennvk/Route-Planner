import { Component, computed, inject } from '@angular/core';
import { CompanyContext } from '../core/company-context';
import { formatDay, shiftDate, todayIn } from '../core/time';
import { Icon } from './icon';

// One date shared across the dashboard, orders, shifts and planner, in the company's timezone
@Component({
  selector: 'app-date-nav',
  imports: [Icon],
  template: `
    <div class="date-nav">
      <button type="button" class="btn btn-icon" aria-label="Previous day" (click)="move(-1)"><app-icon name="chevron-left" /></button>
      <input class="input" type="date" aria-label="Date" [value]="ctx.date()" (change)="pick($any($event.target).value)" />
      <button type="button" class="btn btn-icon" aria-label="Next day" (click)="move(1)"><app-icon name="chevron-right" /></button>
      @if (!isToday()) { <button type="button" class="btn btn-quiet" (click)="today()">Today</button> }
    </div>
    <span class="visually-hidden" aria-live="polite">{{ label() }}</span>
  `,
  styles: `
    .date-nav { display: flex; align-items: center; gap: 4px; }
    .input { width: 150px; }
  `,
})
export class DateNav {
  protected readonly ctx = inject(CompanyContext);
  protected readonly label = computed(() => formatDay(this.ctx.date()));
  protected readonly isToday = computed(() => this.ctx.date() === todayIn(this.ctx.zone()));

  protected move(days: number): void { this.ctx.date.update((date) => shiftDate(date, days)); }
  protected today(): void { this.ctx.date.set(todayIn(this.ctx.zone())); }
  protected pick(value: string): void { if (value) this.ctx.date.set(value); }
}
