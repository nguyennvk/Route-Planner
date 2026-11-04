import { describe, expect, it } from 'vitest';
import { dateInZone, formatWindow, instantToLocal, localToInstant, shiftDate } from './time';

describe('time', () => {
  it('converts a wall-clock time in a zone to an instant', () => {
    expect(localToInstant('2026-11-02T09:00', 'America/Toronto')).toBe('2026-11-02T14:00:00.000Z');
    expect(localToInstant('2026-07-02T09:00', 'America/Toronto')).toBe('2026-07-02T13:00:00.000Z');
    expect(localToInstant('2026-11-02T09:00', 'Asia/Ho_Chi_Minh')).toBe('2026-11-02T02:00:00.000Z');
  });

  it('gets both sides of a daylight saving change right', () => {
    // Toronto left DST at 02:00 on 2026-11-01
    expect(localToInstant('2026-11-01T00:30', 'America/Toronto')).toBe('2026-11-01T04:30:00.000Z');
    expect(localToInstant('2026-11-01T03:30', 'America/Toronto')).toBe('2026-11-01T08:30:00.000Z');
  });

  it('round-trips through the datetime-local format', () => {
    const local = '2026-11-02T17:45';
    expect(instantToLocal(localToInstant(local, 'America/Vancouver'), 'America/Vancouver')).toBe(local);
  });

  it('picks the calendar day of the zone, not of UTC', () => {
    expect(dateInZone('2026-11-03T02:00:00Z', 'America/Toronto')).toBe('2026-11-02');
  });

  it('steps dates across month ends', () => {
    expect(shiftDate('2026-10-31', 1)).toBe('2026-11-01');
    expect(shiftDate('2026-11-01', -1)).toBe('2026-10-31');
  });

  it('describes open-ended delivery windows', () => {
    expect(formatWindow(null, null, 'UTC')).toBe('Any time');
    expect(formatWindow('2026-11-02T09:00:00Z', '2026-11-02T11:30:00Z', 'UTC')).toBe('09:00 – 11:30');
    expect(formatWindow('2026-11-02T09:00:00Z', null, 'UTC')).toBe('From 09:00');
  });
});
