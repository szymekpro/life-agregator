package com.szymek.board.lmb.integration.fitness;

import java.time.LocalDate;

import com.szymek.board.lmb.dashboard.sync.DayDataFetchException;
import com.szymek.board.lmb.dashboard.sync.DayDataProvider;
import com.szymek.board.lmb.dashboard.sync.DayPayload;

abstract class FetcherDayDataProvider implements DayDataProvider {

    private final FetcherClient client;

    protected FetcherDayDataProvider(FetcherClient client) {
        this.client = client;
    }

    protected abstract String path();

    protected abstract Class<? extends FetcherResponse> responseType();

    @Override
    public final DayPayload fetch(LocalDate day) {
        FetcherResponse response = client.get(path(), day, responseType());
        if (!response.hasData()) {
            throw new DayDataFetchException("Fetcher " + path() + " returned no data for " + day);
        }
        return response.toPayload();
    }
}
