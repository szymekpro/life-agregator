package com.szymek.board.lmb.dashboard.sync;

/** The data source is down or not configured; retrying other days would only fail again. */
public class DayDataSourceUnavailableException extends DayDataFetchException {

    public DayDataSourceUnavailableException(String message) {
        super(message);
    }

    public DayDataSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
