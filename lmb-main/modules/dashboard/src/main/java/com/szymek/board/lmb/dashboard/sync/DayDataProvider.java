package com.szymek.board.lmb.dashboard.sync;

import java.time.LocalDate;

public interface DayDataProvider {

    /** Never rename once data is stored. */
    String dataset();

    /**
     * @throws DayDataFetchException this day failed; the sync continues
     * @throws DayDataSourceUnavailableException the source is down; the sync run stops
     */
    DayPayload fetch(LocalDate day);
}
