package com.szymek.board.lmb.dashboard.food;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.szymek.board.lmb.dashboard.InvalidRequestException;
import com.szymek.board.lmb.dashboard.sync.DayDataRepository;
import com.szymek.board.lmb.dashboard.sync.DaySyncProperties;
import com.szymek.board.lmb.dashboard.sync.DaySyncResult;
import com.szymek.board.lmb.dashboard.sync.DaySyncService;
import com.szymek.board.lmb.dashboard.sync.StoredDay;
import org.springframework.stereotype.Service;

/** A day exists when it has meals; targets are optional. */
@Service
public class FoodDayService {

    static final int MAX_RANGE_DAYS = 366;

    private final DayDataRepository repository;
    private final DaySyncService syncService;
    private final DaySyncProperties properties;

    public FoodDayService(DayDataRepository repository, DaySyncService syncService, DaySyncProperties properties) {
        this.repository = repository;
        this.syncService = syncService;
        this.properties = properties;
    }

    /** Falls back to the stored row when the refresh fails. */
    public FoodDay today() {
        LocalDate today = syncService.currentDay();
        DaySyncResult result = syncService.syncToday();
        List<FoodDay> days = days(today, today);
        if (days.isEmpty()) {
            throw new FoodDayUnavailableException(describeFailure(result, today));
        }
        return days.getFirst();
    }

    /** Database only, never the fetcher. */
    public List<FoodDay> days(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : syncService.currentDay();
        LocalDate start = from != null ? from : end.minusDays(properties.getLookbackDays() - 1L);
        validateRange(start, end);

        Map<LocalDate, StoredDay> targetsByDay = new HashMap<>();
        for (StoredDay targets : repository.findBetween(FoodDatasets.TARGETS, start, end, MacroTargetsDay.class)) {
            targetsByDay.put(targets.getDay(), targets);
        }

        List<FoodDay> result = new ArrayList<>();
        for (StoredDay meals : repository.findBetween(FoodDatasets.MEALS, start, end, MealsDay.class)) {
            result.add(assemble(meals, targetsByDay.get(meals.getDay())));
        }
        return result;
    }

    private static void validateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new InvalidRequestException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidRequestException("range must not exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    private static FoodDay assemble(StoredDay meals, StoredDay targets) {
        MealsDay eaten = (MealsDay) meals.getPayload();
        MacroTargetsDay target = targets != null ? (MacroTargetsDay) targets.getPayload() : new MacroTargetsDay();

        Instant syncedAt = meals.getSyncedAt();
        if (targets != null && targets.getSyncedAt().isBefore(syncedAt)) {
            syncedAt = targets.getSyncedAt();
        }

        return new FoodDay(
                meals.getDay(),
                new FoodDay.Progress(eaten.getCalories(), target.getCalories()),
                new FoodDay.Progress(eaten.getProtein(), target.getProteinG()),
                new FoodDay.Progress(eaten.getFat(), target.getFatG()),
                new FoodDay.Progress(eaten.getCarbohydrate(), target.getCarbohydrateG()),
                target.getMode(),
                eaten.getMeals(),
                syncedAt);
    }

    private static String describeFailure(DaySyncResult result, LocalDate today) {
        DaySyncResult.DatasetResult meals = result.getDatasets().get(FoodDatasets.MEALS);
        if (meals != null && !meals.getFailed().isEmpty()) {
            return "Could not sync food data for " + today + ": " + meals.getFailed().getFirst().getReason();
        }
        return "No food data for " + today + " yet";
    }
}
