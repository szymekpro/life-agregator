package com.szymek.board.lmb.integration.fitness;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.szymek.board.lmb.dashboard.food.MacroTargetsDay;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class TargetsResponse extends FetcherResponse {

    private TargetsData data;

    @Override
    public boolean hasData() {
        return data != null;
    }

    @Override
    public MacroTargetsDay toPayload() {
        Macros macros = data.getMacros() == null ? new Macros() : data.getMacros();
        return new MacroTargetsDay(
                data.getMode(),
                data.getCalories(),
                macros.getProteinG(),
                macros.getFatG(),
                macros.getCarbohydrateG());
    }

    @Getter
    @Setter
    static class TargetsData {
        private Double calories;
        private String mode;
        private Macros macros;
    }

    @Getter
    @Setter
    static class Macros {
        @JsonProperty("protein_g")
        private Double proteinG;

        @JsonProperty("fat_g")
        private Double fatG;

        @JsonProperty("carbohydrate_g")
        private Double carbohydrateG;
    }
}
