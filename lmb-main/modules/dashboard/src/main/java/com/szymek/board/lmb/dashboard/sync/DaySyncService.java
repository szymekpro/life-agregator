package com.szymek.board.lmb.dashboard.sync;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.szymek.board.lmb.dashboard.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DaySyncService {

    public static final int MAX_DAYS = 90;

    private static final Logger log = LoggerFactory.getLogger(DaySyncService.class);

    private final List<DayDataProvider> providers;
    private final DayDataRepository repository;
    private final DayFreshnessPolicy freshnessPolicy;
    private final DaySyncProperties properties;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    public DaySyncService(
            List<DayDataProvider> providers,
            DayDataRepository repository,
            DayFreshnessPolicy freshnessPolicy,
            DaySyncProperties properties,
            Clock clock) {
        requireUniqueDatasets(providers);
        this.providers = List.copyOf(providers);
        this.repository = repository;
        this.freshnessPolicy = freshnessPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    public LocalDate currentDay() {
        return freshnessPolicy.today();
    }

    public DaySyncResult sync(Integer days, boolean force) {
        int window = days == null ? properties.getLookbackDays() : days;
        if (window < 1 || window > MAX_DAYS) {
            throw new InvalidRequestException("days must be between 1 and " + MAX_DAYS);
        }
        lock.lock();
        try {
            return run(lastDays(window), force);
        } finally {
            lock.unlock();
        }
    }

    /** Gives up when another sync holds the lock, so a long back-fill does not block reads. */
    public DaySyncResult syncToday() {
        if (!lock.tryLock()) {
            return DaySyncResult.nothingDone();
        }
        try {
            return run(List.of(currentDay()), false);
        } finally {
            lock.unlock();
        }
    }

    private DaySyncResult run(List<LocalDate> daysNewestFirst, boolean force) {
        Map<String, Map<LocalDate, Instant>> syncTimes = loadSyncTimes(daysNewestFirst);
        DaySyncReport report = new DaySyncReport(providers);

        for (LocalDate day : daysNewestFirst) {
            for (DayDataProvider provider : providers) {
                Instant syncedAt = syncTimes.get(provider.dataset()).get(day);
                boolean sourceAvailable = syncDataset(provider, day, syncedAt, force, report);
                if (!sourceAvailable) {
                    return report.build(true);
                }
            }
        }
        return report.build(false);
    }

    private boolean syncDataset(
            DayDataProvider provider, LocalDate day, Instant syncedAt, boolean force, DaySyncReport report) {
        String dataset = provider.dataset();
        if (!force && !freshnessPolicy.needsSync(day, syncedAt)) {
            report.skipped(dataset);
            return true;
        }

        DayPayload payload;
        try {
            payload = provider.fetch(day);
        } catch (DayDataSourceUnavailableException e) {
            log.warn("Day sync aborted, source unavailable ({} {}): {}", dataset, day, e.getMessage());
            report.failed(dataset, day, e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.warn("Day sync failed for {} {}: {}", dataset, day, e.getMessage());
            report.failed(dataset, day, e.getMessage());
            return true;
        }

        repository.save(dataset, day, payload, clock.instant());
        report.synced(dataset, day);
        return true;
    }

    private Map<String, Map<LocalDate, Instant>> loadSyncTimes(List<LocalDate> daysNewestFirst) {
        LocalDate newest = daysNewestFirst.getFirst();
        LocalDate oldest = daysNewestFirst.getLast();
        Map<String, Map<LocalDate, Instant>> syncTimes = new HashMap<>();
        for (DayDataProvider provider : providers) {
            syncTimes.put(provider.dataset(), repository.findSyncedAt(provider.dataset(), oldest, newest));
        }
        return syncTimes;
    }

    private List<LocalDate> lastDays(int count) {
        LocalDate today = currentDay();
        List<LocalDate> days = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            days.add(today.minusDays(i));
        }
        return days;
    }

    private static void requireUniqueDatasets(List<DayDataProvider> providers) {
        Set<String> keys = new HashSet<>();
        for (DayDataProvider provider : providers) {
            if (!keys.add(provider.dataset())) {
                throw new IllegalStateException("Duplicate DayDataProvider dataset: " + provider.dataset());
            }
        }
    }
}
