package com.szymek.board.lmb.dashboard.sync;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DaySyncResult {

    private boolean aborted;

    private Map<String, DatasetResult> datasets;

    public static DaySyncResult nothingDone() {
        return new DaySyncResult(false, Collections.emptyMap());
    }

    @Getter
    @AllArgsConstructor
    public static class DatasetResult {
        private List<LocalDate> synced;
        private int skippedExisting;
        private List<FailedDay> failed;
    }

    @Getter
    @AllArgsConstructor
    public static class FailedDay {
        private LocalDate day;
        private String reason;
    }
}
