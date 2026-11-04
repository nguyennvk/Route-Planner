import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Api, MAX_PAGE_SIZE, fetchAll } from '../../core/api';
import { CompanyContext } from '../../core/company-context';
import { VEHICLE_TYPES, Vehicle, VehicleRequest, VehicleType } from '../../core/models';
import { AddressChoice, AddressPicker } from '../../shared/address-picker';
import { Confirm } from '../../shared/confirm';
import { Field } from '../../shared/field';
import { FormRunner } from '../../shared/form-runner';
import { Icon } from '../../shared/icon';
import { LoadState } from '../../shared/load-state';
import { Modal } from '../../shared/modal';

@Component({
  selector: 'app-fleet',
  imports: [ReactiveFormsModule, AddressPicker, Confirm, Field, Icon, LoadState, Modal],
  templateUrl: './fleet.html',
  styles: `
    .grid { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 20px; align-items: start; }
    .side-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
    .depot { padding: 11px 14px 9px; }
    .depot + .depot { border-top: 1px solid var(--line); }
    .depot strong, .depot span { display: block; }
    .toolbar select { text-transform: capitalize; }
    .cap { text-transform: capitalize; }
    @media (max-width: 1040px) { .grid { grid-template-columns: minmax(0, 1fr); } }
  `,
})
export class Fleet {
  private readonly api = inject(Api);
  protected readonly ctx = inject(CompanyContext);

  protected readonly types = VEHICLE_TYPES;

  protected readonly showInactive = signal(false);
  protected readonly typeFilter = signal<VehicleType | null>(null);

  protected readonly depots = rxResource({
    params: () => this.ctx.id(),
    stream: ({ params: id }) => fetchAll((page) => this.api.depots(id, { page, size: MAX_PAGE_SIZE })),
  });
  protected readonly vehicles = rxResource({
    params: () => ({ id: this.ctx.id(), active: this.showInactive() ? null : true, type: this.typeFilter() }),
    stream: ({ params }) => fetchAll((page) => this.api.vehicles(params.id, { active: params.active, type: params.type, page, size: MAX_PAGE_SIZE })),
  });

  protected readonly hasDepots = computed(() => this.depots.hasValue() && this.depots.value().length > 0);
  private readonly depotNames = computed(() => new Map((this.depots.hasValue() ? this.depots.value() : []).map((depot) => [depot.id, depot.name])));

  protected depotName(id: number): string {
    return this.depotNames().get(id) ?? `Depot #${id}`;
  }

  // Vehicle dialog
  protected readonly vehicleOpen = signal(false);
  protected readonly editing = signal<Vehicle | null>(null);
  protected readonly vehicleRunner = new FormRunner();
  protected readonly vehicleForm = inject(FormBuilder).group({
    name: ['', [Validators.required, Validators.maxLength(254)]],
    type: ['van' as VehicleType, Validators.required],
    capacityKg: [null as number | null, [Validators.required, Validators.min(0.01)]],
    capacityM3: [null as number | null, Validators.min(0.001)],
    homeDepotId: [null as number | null, Validators.required],
  });

  protected openVehicle(vehicle: Vehicle | null): void {
    this.editing.set(vehicle);
    const firstDepot = this.depots.hasValue() ? this.depots.value()[0]?.id ?? null : null;
    this.vehicleForm.reset({
      name: vehicle?.name ?? '', type: vehicle?.type ?? 'van', capacityKg: vehicle?.capacityKg ?? null,
      capacityM3: vehicle?.capacityM3 ?? null, homeDepotId: vehicle?.homeDepotId ?? firstDepot,
    });
    this.vehicleRunner.reset();
    this.vehicleOpen.set(true);
  }

  protected saveVehicle(): void {
    if (this.vehicleForm.invalid) {
      this.vehicleForm.markAllAsTouched();
      return;
    }
    const value = this.vehicleForm.getRawValue();
    const body: VehicleRequest = {
      name: value.name!, type: value.type!, capacityKg: value.capacityKg!, homeDepotId: Number(value.homeDepotId),
      capacityM3: value.capacityM3 ?? undefined,
    };
    const editing = this.editing();
    const request = editing ? this.api.updateVehicle(editing.id, body) : this.api.createVehicle(this.ctx.id(), body);
    this.vehicleRunner.run(request, () => {
      this.vehicleOpen.set(false);
      this.vehicles.reload();
    }, this.vehicleForm);
  }

  // Deactivate and reactivate
  protected readonly deactivating = signal<Vehicle | null>(null);
  protected readonly deactivateRunner = new FormRunner();
  protected readonly deactivateMessage = computed(() => `${this.deactivating()?.name ?? ''} stays in your records but can't be given new shifts. You can reactivate it later.`);

  protected deactivate(): void {
    const vehicle = this.deactivating();
    if (!vehicle) return;
    this.deactivateRunner.run(this.api.deactivateVehicle(vehicle.id), () => {
      this.deactivating.set(null);
      this.vehicles.reload();
    });
  }

  protected reactivate(vehicle: Vehicle): void {
    this.deactivateRunner.run(this.api.updateVehicle(vehicle.id, { active: true }), () => this.vehicles.reload());
  }

  // Depot dialog
  protected readonly depotOpen = signal(false);
  protected readonly depotRunner = new FormRunner();
  protected readonly depotNameControl = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(254)] });
  protected readonly depotAddress = signal<AddressChoice | null>(null);

  protected openDepot(): void {
    this.depotNameControl.reset();
    this.depotAddress.set(null);
    this.depotRunner.reset();
    this.depotOpen.set(true);
  }

  protected saveDepot(): void {
    const choice = this.depotAddress();
    if (this.depotNameControl.invalid || choice?.kind !== 'new') {
      this.depotNameControl.markAsTouched();
      this.depotRunner.error.set(choice ? null : 'Choose an address for the depot.');
      return;
    }
    this.depotRunner.run(this.api.createDepot(this.ctx.id(), { name: this.depotNameControl.value.trim(), address: choice.request }), () => {
      this.depotOpen.set(false);
      this.depots.reload();
    });
  }
}
