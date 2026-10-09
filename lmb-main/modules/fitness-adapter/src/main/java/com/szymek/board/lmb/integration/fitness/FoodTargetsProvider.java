package com.szymek.board.lmb.integration.fitness;

import com.szymek.board.lmb.dashboard.food.FoodDatasets;
import org.springframework.stereotype.Component;

@Component
class FoodTargetsProvider extends FetcherDayDataProvider {

    FoodTargetsProvider(FetcherClient client) {
        super(client);
    }

    @Override
    public String dataset() {
        return FoodDatasets.TARGETS;
    }

    @Override
    protected String path() {
        return "/api/v1/fitatu/targets";
    }

    @Override
    protected Class<? extends FetcherResponse> responseType() {
        return TargetsResponse.class;
    }
}
