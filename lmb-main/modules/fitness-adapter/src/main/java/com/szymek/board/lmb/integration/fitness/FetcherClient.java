package com.szymek.board.lmb.integration.fitness;

import java.time.LocalDate;

public interface FetcherClient {

    FetcherResponse get(String path, LocalDate day, Class<? extends FetcherResponse> type);
}
