package com.szymek.board.lmb.dashboard.sync;

import java.time.Instant;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StoredDay {
    private LocalDate day;
    private DayPayload payload;
    private Instant syncedAt;
}
