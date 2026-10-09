package com.szymek.board.lmb.dashboard.food;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FoodDay {

    private LocalDate day;
    private Progress calories;
    private Progress protein;
    private Progress fat;
    private Progress carbohydrate;
    private String targetMode;
    private List<MealsDay.Meal> meals;

    private Instant syncedAt;

    /** {@code target} is null when Fitatu sent none. */
    @Getter
    @AllArgsConstructor
    public static class Progress {
        private double consumed;
        private Double target;
    }
}
