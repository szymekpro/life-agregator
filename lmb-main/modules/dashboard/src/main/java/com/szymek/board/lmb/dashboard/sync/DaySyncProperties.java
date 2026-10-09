package com.szymek.board.lmb.dashboard.sync;

import java.time.ZoneId;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("lmb.day-sync")
public class DaySyncProperties {

    private int lookbackDays = 30;

    private int staleTodayMinutes = 15;

    private String zone = "Europe/Warsaw";

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }
}
