package com.szymek.board.lmb.dashboard.sync;

import java.time.Instant;
import java.time.LocalDate;

public interface DayFreshnessPolicy {

    LocalDate today();

    /** @param syncedAt when the day was last stored, or null if it never was */
    boolean needsSync(LocalDate day, Instant syncedAt);
}
