package com.szymek.board.lmb.dashboard.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.szymek.board.lmb.dashboard.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DaySyncServiceTest {

    // 10:00 in Warsaw (UTC+2) on 2026-10-04
    private static final Instant NOW = Instant.parse("2026-10-04T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-04");

    private final InMemoryRepository repository = new InMemoryRepository();
    private final FakeProvider meals = new FakeProvider("test.meals");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    // defaults: 30 days lookback, today is stale after 15 minutes, zone Europe/Warsaw
    private final DaySyncProperties properties = new DaySyncProperties();
    private final DayFreshnessPolicy freshnessPolicy = new ConfiguredDayFreshnessPolicy(properties, clock);

    private DaySyncService service;

    @BeforeEach
    void setUp() {
        service = serviceWith(meals);
    }

    @Test
    void emptyDatabaseFillsTheWholeWindowNewestFirst() {
        DaySyncResult result = service.sync(3, false);

        assertThat(meals.fetched).containsExactly(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));
        assertThat(result.isAborted()).isFalse();
        assertThat(result.getDatasets().get("test.meals").getSynced()).hasSize(3);
        assertThat(repository.syncedAt("test.meals", TODAY)).isEqualTo(NOW);
    }

    @Test
    void secondRunOnlyFetchesWhatIsMissing() {
        service.sync(3, false);
        meals.fetched.clear();

        DaySyncResult result = service.sync(3, false);

        assertThat(meals.fetched).isEmpty();
        assertThat(result.getDatasets().get("test.meals").getSkippedExisting()).isEqualTo(3);
    }

    @Test
    void pastDaySyncedBeforeItEndedIsFetchedAgain() {
        // synced 22:00 local on the 3rd, i.e. while that day was still going on
        repository.put("test.meals", TODAY.minusDays(1), Instant.parse("2026-10-03T20:00:00Z"));
        // synced 07:00 local on the 4th, i.e. after the 3rd was over -> final
        repository.put("test.meals", TODAY.minusDays(2), Instant.parse("2026-10-04T05:00:00Z"));
        repository.put("test.meals", TODAY, NOW);

        service.sync(3, false);

        assertThat(meals.fetched).containsExactly(TODAY.minusDays(1));
    }

    @Test
    void todayIsRefreshedOnlyWhenStale() {
        repository.put("test.meals", TODAY, NOW.minusSeconds(5 * 60));
        service.syncToday();
        assertThat(meals.fetched).isEmpty();

        repository.put("test.meals", TODAY, NOW.minusSeconds(20 * 60));
        service.syncToday();
        assertThat(meals.fetched).containsExactly(TODAY);
    }

    @Test
    void forceFetchesEverythingInTheWindow() {
        service.sync(2, false);
        meals.fetched.clear();

        service.sync(2, true);

        assertThat(meals.fetched).containsExactly(TODAY, TODAY.minusDays(1));
    }

    @Test
    void oneFailingDayDoesNotStopTheOthers() {
        meals.failingDays.add(TODAY.minusDays(1));

        DaySyncResult result = service.sync(3, false);

        DaySyncResult.DatasetResult dataset = result.getDatasets().get("test.meals");
        assertThat(result.isAborted()).isFalse();
        assertThat(dataset.getSynced()).containsExactly(TODAY, TODAY.minusDays(2));
        assertThat(dataset.getFailed()).hasSize(1);
        assertThat(dataset.getFailed().get(0).getDay()).isEqualTo(TODAY.minusDays(1));
        // the failed day stays missing, so the next sync retries it
        assertThat(repository.syncedAt("test.meals", TODAY.minusDays(1))).isNull();
    }

    @Test
    void unavailableSourceStopsTheRun() {
        meals.unavailable = true;

        DaySyncResult result = service.sync(5, false);

        assertThat(result.isAborted()).isTrue();
        assertThat(meals.fetched).hasSize(1);
        assertThat(result.getDatasets().get("test.meals").getFailed()).hasSize(1);
    }

    @Test
    void newlyAddedProviderBackfillsWithoutRefetchingTheOthers() {
        service.sync(3, false);
        meals.fetched.clear();

        FakeProvider weight = new FakeProvider("test.weight");
        serviceWith(meals, weight).sync(3, false);

        assertThat(meals.fetched).isEmpty();
        assertThat(weight.fetched).containsExactly(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));
    }

    @Test
    void rejectsDuplicateDatasetKeys() {
        assertThatThrownBy(() -> serviceWith(meals, new FakeProvider("test.meals")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.meals");
    }

    @Test
    void rejectsWindowOutsideTheAllowedRange() {
        assertThatThrownBy(() -> service.sync(0, false)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.sync(DaySyncService.MAX_DAYS + 1, false))
                .isInstanceOf(InvalidRequestException.class);
    }

    private DaySyncService serviceWith(DayDataProvider... providers) {
        return new DaySyncService(List.of(providers), repository, freshnessPolicy, properties, clock);
    }

    private static final class TestPayload implements DayPayload {
    }

    private static final class FakeProvider implements DayDataProvider {

        private final String dataset;
        private final List<LocalDate> fetched = new ArrayList<>();
        private final Set<LocalDate> failingDays = new HashSet<>();
        private boolean unavailable;

        private FakeProvider(String dataset) {
            this.dataset = dataset;
        }

        @Override
        public String dataset() {
            return dataset;
        }

        @Override
        public DayPayload fetch(LocalDate day) {
            fetched.add(day);
            if (unavailable) {
                throw new DayDataSourceUnavailableException("fetcher down");
            }
            if (failingDays.contains(day)) {
                throw new DayDataFetchException("boom " + day);
            }
            return new TestPayload();
        }
    }

    private static final class InMemoryRepository implements DayDataRepository {

        private final Map<String, TreeMap<LocalDate, StoredDay>> rows = new HashMap<>();

        void put(String dataset, LocalDate day, Instant syncedAt) {
            save(dataset, day, new TestPayload(), syncedAt);
        }

        Instant syncedAt(String dataset, LocalDate day) {
            StoredDay row = rowsOf(dataset).get(day);
            return row == null ? null : row.getSyncedAt();
        }

        @Override
        public void save(String dataset, LocalDate day, DayPayload payload, Instant syncedAt) {
            rows.computeIfAbsent(dataset, key -> new TreeMap<>()).put(day, new StoredDay(day, payload, syncedAt));
        }

        @Override
        public Map<LocalDate, Instant> findSyncedAt(String dataset, LocalDate from, LocalDate to) {
            Map<LocalDate, Instant> result = new HashMap<>();
            for (StoredDay row : rowsOf(dataset).subMap(from, true, to, true).values()) {
                result.put(row.getDay(), row.getSyncedAt());
            }
            return result;
        }

        @Override
        public List<StoredDay> findBetween(
                String dataset, LocalDate from, LocalDate to, Class<? extends DayPayload> payloadType) {
            return new ArrayList<>(rowsOf(dataset).subMap(from, true, to, true).values());
        }

        private TreeMap<LocalDate, StoredDay> rowsOf(String dataset) {
            TreeMap<LocalDate, StoredDay> datasetRows = rows.get(dataset);
            return datasetRows != null ? datasetRows : new TreeMap<>(Collections.emptyMap());
        }
    }
}
