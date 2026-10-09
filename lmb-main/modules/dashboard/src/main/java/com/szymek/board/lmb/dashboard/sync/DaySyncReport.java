package com.szymek.board.lmb.dashboard.sync;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DaySyncReport {

    private final Map<String, DatasetProgress> progressByDataset = new LinkedHashMap<>();

    DaySyncReport(List<DayDataProvider> providers) {
        for (DayDataProvider provider : providers) {
            progressByDataset.put(provider.dataset(), new DatasetProgress());
        }
    }

    void synced(String dataset, LocalDate day) {
        progressByDataset.get(dataset).synced.add(day);
    }

    void skipped(String dataset) {
        progressByDataset.get(dataset).skippedExisting++;
    }

    void failed(String dataset, LocalDate day, String reason) {
        progressByDataset.get(dataset).failed.add(new DaySyncResult.FailedDay(day, reason));
    }

    DaySyncResult build(boolean aborted) {
        Map<String, DaySyncResult.DatasetResult> datasets = new LinkedHashMap<>();
        for (Map.Entry<String, DatasetProgress> entry : progressByDataset.entrySet()) {
            DatasetProgress progress = entry.getValue();
            datasets.put(entry.getKey(), new DaySyncResult.DatasetResult(
                    progress.synced, progress.skippedExisting, progress.failed));
        }
        return new DaySyncResult(aborted, datasets);
    }

    private static class DatasetProgress {
        private final List<LocalDate> synced = new ArrayList<>();
        private final List<DaySyncResult.FailedDay> failed = new ArrayList<>();
        private int skippedExisting;
    }
}
