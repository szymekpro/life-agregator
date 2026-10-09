package com.szymek.board.lmb.dashboard;

import java.time.Clock;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan
public class DashboardModuleConfiguration {

    @Bean
    Clock dashboardClock() {
        return Clock.systemUTC();
    }
}
