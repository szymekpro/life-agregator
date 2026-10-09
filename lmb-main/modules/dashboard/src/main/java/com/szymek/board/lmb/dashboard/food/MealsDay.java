package com.szymek.board.lmb.dashboard.food;

import java.util.List;

import com.szymek.board.lmb.dashboard.sync.DayPayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MealsDay implements DayPayload {

    private double calories;
    private double protein;
    private double fat;
    private double carbohydrate;
    private List<Meal> meals;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meal {
        private String name;
        private double calories;
        private double protein;
        private double fat;
        private double carbohydrate;
        private List<Item> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private String name;
        private double calories;
        private double protein;
        private double fat;
        private double carbohydrate;
        private boolean eaten;
    }
}
