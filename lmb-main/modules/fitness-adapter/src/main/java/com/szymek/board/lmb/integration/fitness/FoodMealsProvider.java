package com.szymek.board.lmb.integration.fitness;

import com.szymek.board.lmb.dashboard.food.FoodDatasets;
import org.springframework.stereotype.Component;

@Component
class FoodMealsProvider extends FetcherDayDataProvider {

    FoodMealsProvider(FetcherClient client) {
        super(client);
    }

    @Override
    public String dataset() {
        return FoodDatasets.MEALS;
    }

    @Override
    protected String path() {
        return "/api/v1/fitatu/daily";
    }

    @Override
    protected Class<? extends FetcherResponse> responseType() {
        return DailyResponse.class;
    }
}
