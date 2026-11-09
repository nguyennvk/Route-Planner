import { Component, DestroyRef, ElementRef, afterNextRender, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import type { Marker, Popup } from 'maplibre-gl';
import { DEFAULT_CENTER, MAP_STYLE, MapLibreMap, loadMapLibre } from './maplibre-loader';

export interface MapStop { label: string; title: string; detail: string; latitude: number; longitude: number; }
export interface MapRoute { id: number; fill: string; ink: string; start: [number, number] | null; stops: MapStop[]; path: [number, number][] | null; }
export interface MapDepot { name: string; latitude: number; longitude: number; }

type Maplibre = typeof import('maplibre-gl');
const LINE_SOURCE = 'route-lines';

// Draws routes, loose stops and depots. A route with a road path follows the roads; one without (an older
// saved plan, or OSRM couldn't route it) joins its stops in visiting order with straight segments.
@Component({
  selector: 'app-route-map',
  template: `
    <div #container class="map" role="img" [attr.aria-label]="summary()"></div>
    @if (failed()) { <p class="failed">The map couldn't load. Every stop is still listed below.</p> }
  `,
  styles: `
    :host { display: block; position: relative; }
    .map { height: var(--map-height, 420px); border-radius: var(--radius-l); border: 1px solid var(--line); background: var(--surface-sunken); overflow: hidden; }
    .failed { position: absolute; inset: 50% 0 auto; text-align: center; color: var(--ink-muted); }
  `,
})
export class RouteMap {
  readonly routes = input<MapRoute[]>([]);
  readonly looseStops = input<MapStop[]>([]);
  readonly depots = input<MapDepot[]>([]);
  readonly highlighted = input<number | null>(null);

  protected readonly failed = signal(false);
  protected readonly summary = computed(() => {
    const routes = this.routes();
    return routes.length ? `Map of ${routes.length} routes. Each stop is also listed on this page.` : `Map of ${this.looseStops().length} delivery stops.`;
  });

  private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
  private readonly ready = signal(false);
  private maplibre: Maplibre | null = null;
  private map: MapLibreMap | null = null;
  private markers: { routeId: number | null; marker: Marker; element: HTMLElement }[] = [];
  private popups: Popup[] = [];
  private lastFit = '';

  constructor() {
    inject(DestroyRef).onDestroy(() => this.map?.remove());
    afterNextRender(() => void this.init());

    effect(() => {
      const routes = this.routes();
      const loose = this.looseStops();
      const depots = this.depots();
      if (this.ready()) this.draw(routes, loose, depots);
    });

    effect(() => {
      const highlighted = this.highlighted();
      if (this.ready()) this.applyHighlight(highlighted);
    });
  }

  private async init(): Promise<void> {
    try {
      this.maplibre = await loadMapLibre();
      const map = new this.maplibre.Map({
        container: this.container().nativeElement,
        style: MAP_STYLE,
        center: DEFAULT_CENTER,
        zoom: 10,
        attributionControl: { compact: true },
      });
      this.map = map;
      map.addControl(new this.maplibre.NavigationControl({ showCompass: false }));
      map.on('load', () => {
        map.addSource(LINE_SOURCE, { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
        // White casing under each line keeps crossing routes readable
        map.addLayer({ id: 'route-casing', type: 'line', source: LINE_SOURCE, layout: { 'line-join': 'round', 'line-cap': 'round' }, paint: { 'line-color': '#ffffff', 'line-width': 6, 'line-opacity': ['get', 'opacity'] } });
        map.addLayer({ id: 'route-line', type: 'line', source: LINE_SOURCE, layout: { 'line-join': 'round', 'line-cap': 'round' }, paint: { 'line-color': ['get', 'color'], 'line-width': 3, 'line-opacity': ['get', 'opacity'] } });
        this.ready.set(true);
      });
    } catch {
      this.failed.set(true);
    }
  }

  private draw(routes: MapRoute[], loose: MapStop[], depots: MapDepot[]): void {
    const { map, maplibre } = this;
    if (!map || !maplibre) return;

    this.markers.forEach(({ marker }) => marker.remove());
    this.popups.forEach((popup) => popup.remove());
    this.markers = [];
    this.popups = [];
    const bounds = new maplibre.LngLatBounds();

    const addMarker = (element: HTMLElement, routeId: number | null, lng: number, lat: number, stop?: MapStop) => {
      const marker = new maplibre.Marker({ element }).setLngLat([lng, lat]).addTo(map);
      if (stop) {
        const popup = new maplibre.Popup({ offset: 16, closeButton: false, maxWidth: '260px' }).setDOMContent(popupContent(stop));
        marker.setPopup(popup);
        this.popups.push(popup);
      }
      this.markers.push({ routeId, marker, element });
      bounds.extend([lng, lat]);
    };

    for (const depot of depots) {
      addMarker(depotElement(depot.name), null, depot.longitude, depot.latitude);
    }
    for (const stop of loose) {
      addMarker(stopElement(stop, '#ffffff', '#101c18', true), null, stop.longitude, stop.latitude, stop);
    }
    for (const route of routes) {
      for (const stop of route.stops) {
        addMarker(stopElement(stop, route.fill, route.ink, false), route.id, stop.longitude, stop.latitude, stop);
      }
      // The share page has a road path but no pins to frame
      for (const point of route.path ?? []) bounds.extend(point);
    }
    // untracked: reading highlighted() here would make the draw effect rebuild every marker on each hover
    this.setLines(routes, untracked(this.highlighted));

    // Only move the camera when the set of points changes, not on every redraw
    const fitKey = bounds.isEmpty() ? '' : bounds.toArray().flat().join();
    if (fitKey && fitKey !== this.lastFit) {
      this.lastFit = fitKey;
      map.fitBounds(bounds, { padding: 56, maxZoom: 14, duration: 0 });
    }
  }

  private setLines(routes: MapRoute[], highlighted: number | null): void {
    const source = this.map?.getSource(LINE_SOURCE) as import('maplibre-gl').GeoJSONSource | undefined;
    source?.setData({
      type: 'FeatureCollection',
      features: routes.map((route) => {
        const stops = route.stops.map((stop): [number, number] => [stop.longitude, stop.latitude]);
        const coordinates = route.path ?? (route.start ? [route.start, ...stops, route.start] : stops);
        return {
          type: 'Feature',
          properties: { color: route.fill, opacity: highlighted === null || highlighted === route.id ? 0.95 : 0.15 },
          geometry: { type: 'LineString', coordinates },
        };
      }),
    });
  }

  private applyHighlight(highlighted: number | null): void {
    this.setLines(this.routes(), highlighted);
    for (const { routeId, element } of this.markers) {
      element.style.opacity = highlighted === null || routeId === null || routeId === highlighted ? '1' : '0.25';
    }
  }
}

// Text goes in through textContent: customer names are user data and must never be parsed as HTML
function popupContent(stop: MapStop): HTMLElement {
  const root = document.createElement('div');
  root.className = 'map-popup';
  const title = document.createElement('strong');
  title.textContent = stop.title;
  const detail = document.createElement('span');
  detail.textContent = stop.detail;
  root.append(title, detail);
  return root;
}

function stopElement(stop: MapStop, fill: string, ink: string, loose: boolean): HTMLElement {
  const element = document.createElement('button');
  element.type = 'button';
  element.textContent = stop.label;
  element.setAttribute('aria-label', `${stop.title}, ${stop.detail}`);
  element.style.cssText = `width:${loose ? 18 : 26}px;height:${loose ? 18 : 26}px;padding:2px 0 0;border-radius:50%;cursor:pointer;`
    + `background:${fill};color:${ink};border:2px solid ${loose ? '#55645e' : '#ffffff'};`
    + `font:700 12px/1 var(--font);box-shadow:0 1px 3px rgb(0 0 0 / 35%);transition:opacity 150ms;`;
  return element;
}

function depotElement(name: string): HTMLElement {
  const element = document.createElement('div');
  element.title = `Depot: ${name}`;
  element.style.cssText = 'width:22px;height:22px;border-radius:5px;background:#101c18;border:2px solid #ffffff;box-shadow:0 1px 3px rgb(0 0 0 / 35%);';
  return element;
}
