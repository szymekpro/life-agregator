package com.szymek.board.lmb.dashboard.sync;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

/**
 * Default policy, configured by {@link DaySyncProperties}:
 * <ul>
 *   <li>not stored yet - always (this is also how a newly added dataset back-fills);</li>
 *   <li>today - when the stored copy is older than {@code staleTodayMinutes};</li>
 *   <li>a past day - only if it was synced before that day was over, so it may miss late meals.</li>
 * </ul>
 */
@Component
class ConfiguredDayFreshnessPolicy implements DayFreshnessPolicy {

    private final DaySyncProperties properties;
    private final Clock clock;

    ConfiguredDayFreshnessPolicy(DaySyncProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zoneId()));
    }

    @Override
    public boolean needsSync(LocalDate day, Instant syncedAt) {
        if (syncedAt == null) {
            return true;
        }
        if (day.equals(today())) {
            return isOlderThanStaleLimit(syncedAt);
        }
        return wasSyncedBeforeDayEnded(day, syncedAt);
    }

    private boolean isOlderThanStaleLimit(Instant syncedAt) {
        Duration age = Duration.between(syncedAt, clock.instant());
        return age.compareTo(Duration.ofMinutes(properties.getStaleTodayMinutes())) >= 0;
    }

    private boolean wasSyncedBeforeDayEnded(LocalDate day, Instant syncedAt) {
        LocalDate syncedOn = syncedAt.atZone(properties.zoneId()).toLocalDate();
        return !syncedOn.isAfter(day);
    }
}
