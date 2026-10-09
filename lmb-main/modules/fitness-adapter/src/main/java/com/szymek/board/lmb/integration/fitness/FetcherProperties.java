package com.szymek.board.lmb.integration.fitness;

import java.time.Duration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("lmb.fetcher")
public class FetcherProperties {

    private String baseUrl = "http://localhost:5000";

    private Duration connectTimeout = Duration.ofSeconds(3);

    /** One fetcher call fans out to several Fitatu requests. */
    private Duration readTimeout = Duration.ofSeconds(120);
}
