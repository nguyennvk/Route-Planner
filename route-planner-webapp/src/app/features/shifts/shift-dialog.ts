import { Component, computed, effect, inject, input, output, untracked } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, forkJoin, of } from 'rxjs';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { Shift, ShiftRequest } from '../../core/models';
import { instantToLocal, localToInstant } from '../../core/time';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Modal } from '../../shared/modal';

// The new / edit shift form, shared by the Shifts and Plan pages. target: null keeps it closed,
// 'new' opens an empty form for the selected day, a shift opens it for editing. Owner only.
@Component({
  selector: 'app-shift-dialog',
  imports: [ReactiveFormsModule, Field, Modal],
  template: `
    <app-modal [open]="target() !== null" [heading]="editing() ? 'Edit shift' : 'New shift'" (closed)="closed.emit()">
      <form [formGroup]="form" (ngSubmit)="save()" novalidate>
        @if (runner.error()) { <div class="banner banner-error" role="alert" style="margin-bottom:12px">{{ runner.error() }}</div> }
        <div class="form-grid">
          <app-field class="span-2" label="Vehicle" [control]="form.controls.vehicleId">
            <select class="input" formControlName="vehicleId">
              @for (vehicle of vehicles(); track vehicle.id) { <option [ngValue]="vehicle.id">{{ vehicle.name }}</option> }
              @if (editing(); as shift) { @if (!hasVehicle(shift.vehicleId)) { <option [ngValue]="shift.vehicleId">{{ shift.vehicleName }}</option> } }
            </select>
          </app-field>
          <app-field class="span-2" label="Driver" [optional]="true" [hint]="editing()?.driverUserId ? 'A driver can be changed here but not removed yet' : ''" [control]="form.controls.driverUserId">
            <select class="input" formControlName="driverUserId">
              <option [ngValue]="null">No driver yet</option>
              @for (driver of drivers(); track driver.userId) { <option [ngValue]="driver.userId">{{ driver.fullName }} ({{ driver.role }})</option> }
            </select>
          </app-field>
          <app-field label="Starts" [hint]="'In ' + ctx.zone()" [control]="form.controls.startAt"><input class="input" type="datetime-local" formControlName="startAt" /></app-field>
          <app-field label="Ends" [control]="form.controls.endAt"><input class="input" type="datetime-local" formControlName="endAt" /></app-field>
        </div>
        <div class="form-actions">
          <button type="button" class="btn" (click)="closed.emit()">Cancel</button>
          <button class="btn btn-primary" [disabled]="runner.busy()">{{ runner.busy() ? 'Saving…' : 'Save shift' }}</button>
        </div>
      </form>
    </app-modal>
  `,
})
export class ShiftDialog {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);

  readonly target = input<Shift | 'new' | null>(null);
  readonly saved = output<Shift>();
  readonly closed = output<void>();

  protected readonly editing = computed(() => {
    const target = this.target();
    return target === 'new' ? null : target;
  });

  // Loaded the first time the form opens, not with the page
  private readonly lookups = rxResource({
    params: () => (this.target() !== null ? { id: this.ctx.id() } : undefined),
    stream: ({ params: { id } }) => forkJoin({
      vehicles: fetchAll((page) => this.api.vehicles(id, { active: true, page, size: MAX_PAGE_SIZE })),
      members: this.api.members(id),
    }).pipe(catchError(() => of({ vehicles: [], members: [] }))),
  });
  protected readonly vehicles = computed(() => (this.lookups.hasValue() ? this.lookups.value().vehicles : []));
  protected readonly drivers = computed(() => (this.lookups.hasValue() ? this.lookups.value().members : []).filter((m) => m.active));

  protected readonly runner = new FormRunner();
  protected readonly form = inject(FormBuilder).group({
    vehicleId: [null as number | null, Validators.required],
    driverUserId: [null as number | null],
    startAt: ['', Validators.required],
    endAt: ['', Validators.required],
  });

  constructor() {
    // Fill the form each time it opens. untracked: typing must not re-run this
    effect(() => {
      const target = this.target();
      if (target !== null) untracked(() => this.reset(target));
    });

    // A new shift gets the first vehicle once the list arrives (it may still be loading when the form opens)
    effect(() => {
      const first = this.vehicles()[0];
      untracked(() => {
        if (this.target() === 'new' && first && this.form.controls.vehicleId.value === null) {
          this.form.controls.vehicleId.setValue(first.id);
        }
      });
    });
  }

  private reset(target: Shift | 'new'): void {
    this.runner.reset();
    const zone = this.ctx.zone();
    if (target === 'new') {
      const date = this.ctx.date();
      this.form.reset({ vehicleId: this.vehicles()[0]?.id ?? null, driverUserId: null, startAt: `${date}T08:00`, endAt: `${date}T17:00` });
      this.form.controls.vehicleId.enable();
    } else {
      this.form.reset({ vehicleId: target.vehicleId, driverUserId: target.driverUserId, startAt: instantToLocal(target.startAt, zone), endAt: instantToLocal(target.endAt, zone) });
      this.form.controls.vehicleId.disable();   // UpdateVehicleShiftRequest can't move a shift to another vehicle
    }
  }

  protected hasVehicle(id: number): boolean {
    return this.vehicles().some((vehicle) => vehicle.id === id);
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
    this.runner.run(request, (shift) => this.saved.emit(shift), this.form);
  }
}
