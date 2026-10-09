package com.szymek.board.lmb.integration.fitness;

import java.util.ArrayList;
import java.util.List;

import com.szymek.board.lmb.dashboard.food.MealsDay;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
class DailyResponse extends FetcherResponse {

    private DailyData data;

    @Override
    public boolean hasData() {
        return data != null;
    }

    @Override
    public MealsDay toPayload() {
        double caloriesConsumed = data.getCalories() == null ? 0 : number(data.getCalories().getConsumed());
        Macros totals = data.getMacros() == null ? new Macros() : data.getMacros();

        List<MealsDay.Meal> meals = new ArrayList<>();
        if (data.getMeals() != null) {
            for (Meal meal : data.getMeals()) {
                // Fitatu also lists empty meal slots.
                if (meal.getItems() != null && !meal.getItems().isEmpty()) {
                    meals.add(meal.toMeal());
                }
            }
        }

        return new MealsDay(
                caloriesConsumed,
                number(totals.getProtein()),
                number(totals.getFat()),
                number(totals.getCarbohydrate()),
                meals);
    }

    @Getter
    @Setter
    static class DailyData {
        private Amount calories;
        private Macros macros;
        private List<Meal> meals;
    }

    @Getter
    @Setter
    static class Amount {
        private Double consumed;
        private Double target;
    }

    @Getter
    @Setter
    static class Macros {
        private Double protein;
        private Double fat;
        private Double carbohydrate;
    }

    @Getter
    @Setter
    static class Meal {
        private String name;
        private Double calories;
        private Macros macros;
        private List<Item> items;

        MealsDay.Meal toMeal() {
            Macros mealMacros = macros == null ? new Macros() : macros;
            List<MealsDay.Item> mapped = new ArrayList<>();
            for (Item item : items) {
                mapped.add(item.toItem());
            }
            return new MealsDay.Meal(
                    name,
                    number(calories),
                    number(mealMacros.getProtein()),
                    number(mealMacros.getFat()),
                    number(mealMacros.getCarbohydrate()),
                    mapped);
        }
    }

    @Getter
    @Setter
    static class Item {
        private String name;
        private Double calories;
        private Double protein;
        private Double fat;
        private Double carbohydrate;
        private Boolean eaten;

        MealsDay.Item toItem() {
            return new MealsDay.Item(
                    name,
                    number(calories),
                    number(protein),
                    number(fat),
                    number(carbohydrate),
                    Boolean.TRUE.equals(eaten));
        }
    }
}
