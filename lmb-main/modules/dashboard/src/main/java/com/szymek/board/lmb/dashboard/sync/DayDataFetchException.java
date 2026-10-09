package com.szymek.board.lmb.dashboard.sync;

/** Fetching one day of one dataset failed; other days and datasets are still synced. */
public class DayDataFetchException extends RuntimeException {

    public DayDataFetchException(String message) {
        super(message);
    }

    public DayDataFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
