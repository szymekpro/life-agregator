package com.szymek.board.lmb.integration.fitness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.szymek.board.lmb.dashboard.food.MacroTargetsDay;
import com.szymek.board.lmb.dashboard.food.MealsDay;
import com.szymek.board.lmb.dashboard.sync.DayDataFetchException;
import com.szymek.board.lmb.dashboard.sync.DayDataSourceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FetcherProvidersTest {

    private static final LocalDate DAY = LocalDate.parse("2026-10-03");

    private HttpServer server;
    private final List<String> requestedQueries = new ArrayList<>();
    private String dailyBody;
    private String targetsBody;

    @BeforeEach
    void startFetcher() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/fitatu/daily", exchange -> respond(exchange, dailyBody));
        server.createContext("/api/v1/fitatu/targets", exchange -> respond(exchange, targetsBody));
        server.start();
    }

    @AfterEach
    void stopFetcher() {
        server.stop(0);
    }

    @Test
    void mealsProviderMapsDailyAndDropsEmptyMealSlots() {
        dailyBody = """
                {"status":"ok","data":{
                  "date":"2026-10-03",
                  "calories":{"consumed":1850.5,"target":2200},
                  "macros":{"protein":120,"fat":65,"carbohydrate":180,"fiber":25,"sugars":30,"salt":5},
                  "meals":[
                    {"name":"Sniadanie","item_count":1,"calories":450,
                     "macros":{"protein":30,"fat":15,"carbohydrate":50,"fiber":5,"sugars":10,"salt":1},
                     "items":[{"name":"Jajecznica","calories":450,"protein":30,"fat":15,"carbohydrate":50,"fiber":0,"eaten":true}]},
                    {"name":"Kolacja","item_count":0,"calories":0,
                     "macros":{"protein":0,"fat":0,"carbohydrate":0,"fiber":0,"sugars":0,"salt":0},"items":[]}
                  ]}}
                """;

        MealsDay day = (MealsDay) meals().fetch(DAY);

        assertThat(day.getCalories()).isEqualTo(1850.5);
        assertThat(day.getProtein()).isEqualTo(120);
        assertThat(day.getMeals()).hasSize(1);
        assertThat(day.getMeals().get(0).getName()).isEqualTo("Sniadanie");
        assertThat(day.getMeals().get(0).getItems().get(0).isEaten()).isTrue();
        assertThat(requestedQueries).containsExactly("day=2026-10-03");
    }

    @Test
    void targetsProviderMapsSnakeCaseMacros() {
        targetsBody = """
                {"status":"ok","data":{
                  "date":"2026-10-03","calories":2200,"mode":"automatic",
                  "macros":{"protein_g":82.5,"fat_g":73.3,"carbohydrate_g":247.5,"protein_g_min":66,"protein_percent":15}}}
                """;

        MacroTargetsDay targets = (MacroTargetsDay) new FoodTargetsProvider(client()).fetch(DAY);

        assertThat(targets).isEqualTo(new MacroTargetsDay("automatic", 2200.0, 82.5, 73.3, 247.5));
    }

    @Test
    void targetsProviderToleratesDaysWithoutTargets() {
        targetsBody = """
                {"status":"ok","data":{"date":"2026-10-03"}}
                """;

        MacroTargetsDay targets = (MacroTargetsDay) new FoodTargetsProvider(client()).fetch(DAY);

        assertThat(targets).isEqualTo(new MacroTargetsDay(null, null, null, null, null));
    }

    @Test
    void errorStatusFailsOnlyThatDay() {
        dailyBody = """
                {"status":"error","error":"Fitatu API 500","status_code":500}
                """;

        assertThatThrownBy(() -> meals().fetch(DAY))
                .isExactlyInstanceOf(DayDataFetchException.class)
                .hasMessageContaining("Fitatu API 500");
    }

    @Test
    void skippedStatusMeansTheSourceIsNotUsable() {
        dailyBody = """
                {"status":"skipped","error":"Set FITATU_EMAIL and FITATU_PASSWORD in .env"}
                """;

        assertThatThrownBy(() -> meals().fetch(DAY))
                .isInstanceOf(DayDataSourceUnavailableException.class)
                .hasMessageContaining("FITATU_EMAIL");
    }

    @Test
    void unreachableFetcherIsReportedAsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        FetcherClient client = new HttpFetcherClient(properties("http://127.0.0.1:" + closedPort, 1, 1));

        assertThatThrownBy(() -> new FoodMealsProvider(client).fetch(DAY))
                .isInstanceOf(DayDataSourceUnavailableException.class);
    }

    private FoodMealsProvider meals() {
        return new FoodMealsProvider(client());
    }

    private FetcherClient client() {
        return new HttpFetcherClient(properties("http://127.0.0.1:" + server.getAddress().getPort(), 2, 5));
    }

    private static FetcherProperties properties(String baseUrl, int connectSeconds, int readSeconds) {
        FetcherProperties properties = new FetcherProperties();
        properties.setBaseUrl(baseUrl);
        properties.setConnectTimeout(Duration.ofSeconds(connectSeconds));
        properties.setReadTimeout(Duration.ofSeconds(readSeconds));
        return properties;
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        requestedQueries.add(exchange.getRequestURI().getQuery());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
