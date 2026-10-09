package com.szymek.board.lmb.integration.fitness;

import com.szymek.board.lmb.dashboard.sync.DayPayload;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class FetcherResponse {

    /** "ok", "error", or "skipped" when the fetcher has no Fitatu credentials. */
    private String status;

    private String error;

    public abstract boolean hasData();

    public abstract DayPayload toPayload();

    protected static double number(Double value) {
        return value == null ? 0.0 : value;
    }
}
