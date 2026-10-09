package com.szymek.board.lmb.dashboard.food;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.szymek.board.lmb.dashboard.DashboardExceptionHandler;
import com.szymek.board.lmb.dashboard.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FoodDashboardControllerTest {

    private final FoodDayService service = mock(FoodDayService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FoodDashboardController(service))
                .setControllerAdvice(new DashboardExceptionHandler())
                .build();
    }

    @Test
    void todayReturnsTheWidgetShape() throws Exception {
        when(service.today()).thenReturn(sampleDay());

        mvc.perform(get("/api/v1/dashboard/food/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.day").value("2026-10-04"))
                .andExpect(jsonPath("$.calories.consumed").value(1850.0))
                .andExpect(jsonPath("$.calories.target").value(2200.0))
                .andExpect(jsonPath("$.protein.target").value(82.5))
                .andExpect(jsonPath("$.fat.target").doesNotExist())
                .andExpect(jsonPath("$.targetMode").value("automatic"))
                .andExpect(jsonPath("$.meals[0].name").value("Sniadanie"))
                .andExpect(jsonPath("$.meals[0].items[0].eaten").value(true))
                .andExpect(jsonPath("$.syncedAt").value("2026-10-04T08:00:00Z"));
    }

    @Test
    void todayIs503WhenNothingCouldBeSynced() throws Exception {
        when(service.today()).thenThrow(new FoodDayUnavailableException("fetcher down"));

        mvc.perform(get("/api/v1/dashboard/food/today"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("fetcher down"));
    }

    @Test
    void daysPassesTheRangeThrough() throws Exception {
        when(service.days(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-04")))
                .thenReturn(List.of(sampleDay()));

        mvc.perform(get("/api/v1/dashboard/food/days").param("from", "2026-10-01").param("to", "2026-10-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].day").value("2026-10-04"));
    }

    @Test
    void daysRejectsAnInvalidRange() throws Exception {
        when(service.days(any(), any())).thenThrow(new InvalidRequestException("from must not be after to"));

        mvc.perform(get("/api/v1/dashboard/food/days").param("from", "2026-10-05").param("to", "2026-10-04"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("from must not be after to"));
    }

    private static FoodDay sampleDay() {
        MealsDay.Item item = new MealsDay.Item("Jajecznica", 450, 30, 15, 50, true);
        MealsDay.Meal meal = new MealsDay.Meal("Sniadanie", 450, 30, 15, 50, List.of(item));
        return new FoodDay(
                LocalDate.parse("2026-10-04"),
                new FoodDay.Progress(1850.0, 2200.0),
                new FoodDay.Progress(120.0, 82.5),
                new FoodDay.Progress(65.0, null),
                new FoodDay.Progress(180.0, 247.5),
                "automatic",
                List.of(meal),
                Instant.parse("2026-10-04T08:00:00Z"));
    }
}
