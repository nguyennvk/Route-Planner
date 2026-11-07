import type { Map as MapLibreMap, StyleSpecification } from 'maplibre-gl';

// OpenFreeMap: OpenStreetMap vector tiles, free and with no API key
export const MAP_STYLE: string | StyleSpecification = 'https://tiles.openfreemap.org/styles/liberty';

// Toronto, used until the map has real points to fit
export const DEFAULT_CENTER: [number, number] = [-79.38, 43.65];

let cssLoaded: Promise<void> | null = null;

// MapLibre is about 800 kB, so its code and CSS only download once a page actually shows a map
export async function loadMapLibre(): Promise<typeof import('maplibre-gl')> {
  cssLoaded ??= new Promise((resolve, reject) => {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'maplibre.css';
    link.onload = () => resolve();
    link.onerror = () => reject(new Error('Could not load the map styles'));
    document.head.append(link);
  });
  const [maplibre] = await Promise.all([import('maplibre-gl'), cssLoaded]);
  // MapLibre finds its worker relative to its own file, which bundling breaks.
  // angular.json copies the worker to the site root, so point it there.
  maplibre.setWorkerUrl(new URL('maplibre-gl-worker.mjs', document.baseURI).href);
  return maplibre;
}

export type { MapLibreMap };
