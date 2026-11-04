// Browser's IANA zone list, plus the browser's own zone in case the list leaves it out (it can use an older alias)
export function timezoneOptions(extra?: string): string[] {
  const zones = new Set(Intl.supportedValuesOf('timeZone'));
  zones.add(Intl.DateTimeFormat().resolvedOptions().timeZone);
  if (extra) zones.add(extra);
  return [...zones].sort();
}
