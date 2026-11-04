// All times on screen are in the company's timezone, whatever the browser's is.
// JavaScript's Date only knows UTC and the browser's own zone, so going from
// "09:00 in Toronto" to an instant takes the offset lookup below.

function partsIn(zone: string, date: Date): Record<string, number> {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: zone, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric',
    hour: 'numeric', minute: 'numeric', second: 'numeric',
  }).formatToParts(date);
  return Object.fromEntries(parts.filter((p) => p.type !== 'literal').map((p) => [p.type, Number(p.value)]));
}

function offsetMs(zone: string, date: Date): number {
  const p = partsIn(zone, date);
  const asUtc = Date.UTC(p['year'], p['month'] - 1, p['day'], p['hour'], p['minute'], p['second']);
  return asUtc - Math.floor(date.getTime() / 1000) * 1000;
}

// "2026-11-02T09:00" in zone -> "2026-11-02T14:00:00.000Z"
export function localToInstant(local: string, zone: string): string {
  const [datePart, timePart = '00:00'] = local.split('T');
  const [year, month, day] = datePart.split('-').map(Number);
  const [hour, minute, second = 0] = timePart.split(':').map(Number);
  const wallClock = Date.UTC(year, month - 1, day, hour, minute, second);
  // Guess with the offset at the wall-clock time, then correct once in case that guess crossed a DST change
  let instant = wallClock - offsetMs(zone, new Date(wallClock));
  instant = wallClock - offsetMs(zone, new Date(instant));
  return new Date(instant).toISOString();
}

// "2026-11-02T14:00:00Z" in zone -> "2026-11-02T09:00", the format <input type="datetime-local"> uses
export function instantToLocal(instant: string, zone: string): string {
  const p = partsIn(zone, new Date(instant));
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${p['year']}-${pad(p['month'])}-${pad(p['day'])}T${pad(p['hour'])}:${pad(p['minute'])}`;
}

export function dateInZone(instant: string, zone: string): string {
  return instantToLocal(instant, zone).slice(0, 10);
}

export function todayIn(zone: string): string {
  return dateInZone(new Date().toISOString(), zone);
}

export function shiftDate(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

export function formatTime(instant: string | null | undefined, zone: string): string {
  if (!instant) return '–';
  return new Intl.DateTimeFormat('en-CA', { timeZone: zone, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(instant));
}

export function formatDateTime(instant: string | null | undefined, zone: string): string {
  if (!instant) return '–';
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: zone, month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).format(new Date(instant));
}

export function formatWindow(start: string | null, end: string | null, zone: string): string {
  if (!start && !end) return 'Any time';
  if (start && end) return `${formatTime(start, zone)} – ${formatTime(end, zone)}`;
  return start ? `From ${formatTime(start, zone)}` : `By ${formatTime(end, zone)}`;
}

export function formatDay(date: string): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'UTC', weekday: 'long', month: 'long', day: 'numeric' })
    .format(new Date(Date.UTC(year, month - 1, day)));
}

// The plan's arrival times are LocalDateTime with no zone, built with ZoneId.systemDefault() in
// PlanningService: they are wall-clock times in the API server's zone, not the company's.
// Locally the server and browser share a machine, so the browser's zone is the right one to read them in.
// If the API starts returning Instants, delete this and pass them straight to formatTime.
export const API_SERVER_TIMEZONE = Intl.DateTimeFormat().resolvedOptions().timeZone;

export function serverLocalToInstant(local: string | null): string | null {
  return local ? localToInstant(local.slice(0, 19), API_SERVER_TIMEZONE) : null;
}
