import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { Shift } from '../../core/models';
import { formatDay, formatTime, localToInstant, shiftDate } from '../../core/time';
import { Confirm } from '../../shared/confirm';
import { DateNav } from '../../shared/date-nav';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { ShiftDialog } from './shift-dialog';

@Component({
  selector: 'app-shifts',
  imports: [RouterLink, Confirm, DateNav, Icon, LoadState, ShiftDialog],
  templateUrl: './shifts.html',
  styles: `
    .timeline { position: relative; min-width: 220px; height: 22px; background: repeating-linear-gradient(to right, var(--line) 0 1px, transparent 1px 25%); border-radius: var(--radius-s); background-color: var(--gray-50); }
    .bar { position: absolute; top: 3px; bottom: 3px; min-width: 3px; border-radius: 3px; background: var(--brand); }
    .scale { display: flex; justify-content: space-between; min-width: 220px; font-weight: 500; }
    .timeline-cell { width: 34%; }
    @media (max-width: 860px) { .timeline-cell { display: none; } }
  `,
})
export class Shifts {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);

  protected readonly dayLabel = computed(() => formatDay(this.ctx.date()));

  protected readonly shifts = rxResource({
    params: () => ({ id: this.ctx.id(), date: this.ctx.date() }),
    stream: ({ params }) => fetchAll((page) => this.api.shifts(params.id, { date: params.date, page, size: MAX_PAGE_SIZE })),
  });

  // Whether there's an active vehicle to put on a shift at all; the form loads its own lists
  protected readonly lookups = rxResource({
    params: () => this.ctx.id(),
    stream: ({ params: id }) => fetchAll((page) => this.api.vehicles(id, { active: true, page, size: MAX_PAGE_SIZE }))
      .pipe(catchError(() => of([]))),
  });
  protected readonly vehicles = computed(() => (this.lookups.hasValue() ? this.lookups.value() : []));
  protected time(instant: string): string { return formatTime(instant, this.ctx.zone()); }

  // Where a shift sits on the 24-hour bar, clipped to the selected day
  protected bar(shift: Shift): { left: string; width: string } {
    const zone = this.ctx.zone();
    const dayStart = Date.parse(localToInstant(`${this.ctx.date()}T00:00`, zone));
    const dayEnd = Date.parse(localToInstant(`${shiftDate(this.ctx.date(), 1)}T00:00`, zone));
    const length = dayEnd - dayStart;
    const from = Math.max(Date.parse(shift.startAt), dayStart);
    const to = Math.min(Date.parse(shift.endAt), dayEnd);
    return { left: `${((from - dayStart) / length) * 100}%`, width: `${((to - from) / length) * 100}%` };
  }

  // New / edit: the shared dialog. null keeps it closed
  protected readonly dialogTarget = signal<Shift | 'new' | null>(null);

  protected shiftSaved(): void {
    this.dialogTarget.set(null);
    this.shifts.reload();
  }

  // Delete
  protected readonly deleting = signal<Shift | null>(null);
  protected readonly deleteRunner = new FormRunner();
  protected readonly deleteMessage = computed(() => {
    const shift = this.deleting();
    return shift ? `${shift.vehicleName}, ${this.time(shift.startAt)} to ${this.time(shift.endAt)}, won't be available for planning.` : '';
  });

  protected openDelete(shift: Shift): void {
    this.deleteRunner.reset();
    this.deleting.set(shift);
  }

  protected confirmDelete(): void {
    const shift = this.deleting();
    if (!shift) return;
    this.deleteRunner.run(this.api.deleteShift(shift.id), () => {
      this.deleting.set(null);
      this.shifts.reload();
    });
  }
}
