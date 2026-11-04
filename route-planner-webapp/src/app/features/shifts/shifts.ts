import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { Shift, ShiftRequest } from '../../core/models';
import { formatDay, formatTime, instantToLocal, localToInstant, shiftDate } from '../../core/time';
import { Confirm } from '../../shared/confirm';
import { DateNav } from '../../shared/date-nav';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { Modal } from '../../shared/modal';

@Component({
  selector: 'app-shifts',
  imports: [ReactiveFormsModule, RouterLink, Confirm, DateNav, Field, Icon, LoadState, Modal],
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

  protected readonly lookups = rxResource({
    params: () => ({ id: this.ctx.id(), owner: this.ctx.isOwner() }),
    stream: ({ params }) => forkJoin({
      vehicles: fetchAll((page) => this.api.vehicles(params.id, { active: true, page, size: MAX_PAGE_SIZE })),
      // Members are owner-only; anyone else only reads shifts and never opens this form
      members: params.owner ? this.api.members(params.id) : of([]),
    }).pipe(catchError(() => of({ vehicles: [], members: [] }))),
  });
  protected readonly vehicles = computed(() => (this.lookups.hasValue() ? this.lookups.value().vehicles : []));
  protected readonly drivers = computed(() => (this.lookups.hasValue() ? this.lookups.value().members : []).filter((m) => m.active));

  protected hasId(id: number) { return (vehicle: { id: number }) => vehicle.id === id; }
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

  // Form
  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<Shift | null>(null);
  protected readonly runner = new FormRunner();
  protected readonly form = inject(FormBuilder).group({
    vehicleId: [null as number | null, Validators.required],
    driverUserId: [null as number | null],
    startAt: ['', Validators.required],
    endAt: ['', Validators.required],
  });

  protected openNew(): void {
    this.editing.set(null);
    const date = this.ctx.date();
    this.form.reset({ vehicleId: this.vehicles()[0]?.id ?? null, driverUserId: null, startAt: `${date}T08:00`, endAt: `${date}T17:00` });
    this.form.controls.vehicleId.enable();
    this.runner.reset();
    this.dialogOpen.set(true);
  }

  protected openEdit(shift: Shift): void {
    this.editing.set(shift);
    const zone = this.ctx.zone();
    this.form.reset({ vehicleId: shift.vehicleId, driverUserId: shift.driverUserId, startAt: instantToLocal(shift.startAt, zone), endAt: instantToLocal(shift.endAt, zone) });
    this.form.controls.vehicleId.disable();   // UpdateVehicleShiftRequest can't move a shift to another vehicle
    this.runner.reset();
    this.dialogOpen.set(true);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const zone = this.ctx.zone();
    const startAt = localToInstant(value.startAt!, zone);
    const endAt = localToInstant(value.endAt!, zone);
    if (endAt <= startAt) {
      this.runner.error.set('The shift must end after it starts.');
      return;
    }
    const driverUserId = value.driverUserId === null ? undefined : Number(value.driverUserId);
    const editing = this.editing();
    const body: ShiftRequest = { vehicleId: Number(value.vehicleId), startAt, endAt, driverUserId };
    const request = editing
      ? this.api.updateShift(editing.id, { startAt, endAt, driverUserId })
      : this.api.createShift(this.ctx.id(), body);
    this.runner.run(request, () => {
      this.dialogOpen.set(false);
      this.shifts.reload();
    }, this.form);
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
