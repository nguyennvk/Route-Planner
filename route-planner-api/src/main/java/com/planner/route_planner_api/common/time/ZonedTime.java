package com.planner.route_planner_api.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

// Instant is what we store (one moment, same everywhere); LocalDate/LocalDateTime is what a
// company sees on its wall clock. Always convert with the company's zone, never the server's.
// All methods pass null through, so optional times (e.g. an open time window) need no checks.
public final class ZonedTime {

    private ZonedTime() {}

    public static LocalDateTime toLocalDateTime(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, zone);
    }

    // In a DST gap (a wall time that never happens) this moves forward by the gap length;
    // in an overlap (a wall time that happens twice) it picks the earlier moment
    public static Instant toInstant(LocalDateTime localDateTime, ZoneId zone) {
        return localDateTime == null ? null : localDateTime.atZone(zone).toInstant();
    }

    // Which calendar day the moment falls on for this company
    public static LocalDate toLocalDate(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDate.ofInstant(instant, zone);
    }

    // The first moment of that day for this company; use [startOfDay(d), startOfDay(d + 1)) for "all of day d"
    public static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date == null ? null : date.atStartOfDay(zone).toInstant();
    }
}
