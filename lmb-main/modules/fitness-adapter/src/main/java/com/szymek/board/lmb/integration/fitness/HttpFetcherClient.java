package com.szymek.board.lmb.integration.fitness;

import java.net.http.HttpClient;
import java.time.LocalDate;

import com.szymek.board.lmb.dashboard.sync.DayDataFetchException;
import com.szymek.board.lmb.dashboard.sync.DayDataSourceUnavailableException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
class HttpFetcherClient implements FetcherClient {

    private final RestClient restClient;

    HttpFetcherClient(FetcherProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public FetcherResponse get(String path, LocalDate day, Class<? extends FetcherResponse> type) {
        FetcherResponse response = call(path, day, type);
        checkStatus(path, response);
        return response;
    }

    private FetcherResponse call(String path, LocalDate day, Class<? extends FetcherResponse> type) {
        FetcherResponse response;
        try {
            response = restClient.get()
                    .uri(uri -> uri.path(path).queryParam("day", day.toString()).build())
                    .retrieve()
                    .body(type);
        } catch (ResourceAccessException e) {
            throw new DayDataSourceUnavailableException("Fetcher unreachable: " + e.getMessage(), e);
        } catch (RestClientResponseException e) {
            throw new DayDataFetchException("Fetcher " + path + " returned HTTP " + e.getStatusCode().value(), e);
        }
        if (response == null) {
            throw new DayDataFetchException("Fetcher " + path + " returned an empty response");
        }
        return response;
    }

    private static void checkStatus(String path, FetcherResponse response) {
        if ("skipped".equals(response.getStatus())) {
            throw new DayDataSourceUnavailableException("Fetcher is not configured: " + response.getError());
        }
        if (!"ok".equals(response.getStatus())) {
            throw new DayDataFetchException("Fetcher " + path + " failed: " + response.getError());
        }
    }
}
