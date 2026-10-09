package com.szymek.board.lmb.dashboard.sync;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface DayDataRepository {

    void save(String dataset, LocalDate day, DayPayload payload, Instant syncedAt);

    Map<LocalDate, Instant> findSyncedAt(String dataset, LocalDate from, LocalDate to);

    List<StoredDay> findBetween(String dataset, LocalDate from, LocalDate to, Class<? extends DayPayload> payloadType);
}
