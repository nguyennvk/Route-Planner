import { Component, DestroyRef, ElementRef, afterNextRender, effect, inject, input, output, signal, viewChild } from '@angular/core';
import type { Marker } from 'maplibre-gl';
import { DEFAULT_CENTER, MAP_STYLE, MapLibreMap, loadMapLibre } from './maplibre-loader';

export interface LatLng { latitude: number; longitude: number; }

// Click or drag to place one pin. Forms pair it with number inputs so it also works without a pointer.
@Component({
  selector: 'app-pin-map',
  template: `
    <div #container class="map" role="application" aria-label="Map. Click to place the pin, or type coordinates in the fields below."></div>
    @if (failed()) { <p class="field-error">The map couldn't load. You can still type the coordinates.</p> }
  `,
  styles: `
    :host { display: block; }
    .map { height: 260px; border-radius: var(--radius-m); border: 1px solid var(--line-strong); background: var(--surface-sunken); overflow: hidden; }
  `,
})
export class PinMap {
  readonly pin = input<LatLng | null>(null);
  readonly pinChange = output<LatLng>();

  protected readonly failed = signal(false);
  private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
  private map: MapLibreMap | null = null;
  private marker: Marker | null = null;

  constructor() {
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => this.map?.remove());

    afterNextRender(() => void this.init());

    effect(() => this.place(this.pin()));
  }

  private async init(): Promise<void> {
    try {
      const maplibre = await loadMapLibre();
      const start = this.pin();
      this.map = new maplibre.Map({
        container: this.container().nativeElement,
        style: MAP_STYLE,
        center: start ? [start.longitude, start.latitude] : DEFAULT_CENTER,
        zoom: start ? 15 : 9,
        attributionControl: { compact: true },
      });
      this.map.addControl(new maplibre.NavigationControl({ showCompass: false }));
      this.marker = new maplibre.Marker({ color: '#00593f', draggable: true });
      this.marker.on('dragend', () => {
        const { lat, lng } = this.marker!.getLngLat();
        this.pinChange.emit({ latitude: round(lat), longitude: round(lng) });
      });
      this.map.on('click', (event) => this.pinChange.emit({ latitude: round(event.lngLat.lat), longitude: round(event.lngLat.lng) }));
      this.place(start);
    } catch {
      this.failed.set(true);
    }
  }

  private place(pin: LatLng | null): void {
    if (!this.map || !this.marker) return;
    if (pin) {
      const position: [number, number] = [pin.longitude, pin.latitude];
      this.marker.setLngLat(position).addTo(this.map);
      if (!this.map.getBounds().contains(position)) this.map.easeTo({ center: position, zoom: 15 });
    } else {
      this.marker.remove();
    }
  }
}

function round(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}
