package com.szymek.board.lmb.dashboard.food;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/food")
public class FoodDashboardController {

    private final FoodDayService foodDayService;

    public FoodDashboardController(FoodDayService foodDayService) {
        this.foodDayService = foodDayService;
    }

    @GetMapping("/today")
    public FoodDay today() {
        return foodDayService.today();
    }

    @GetMapping("/days")
    public List<FoodDay> days(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return foodDayService.days(from, to);
    }
}
