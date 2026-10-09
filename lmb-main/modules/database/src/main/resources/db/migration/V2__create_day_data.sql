-- Dashboard read model: one row per (dataset, day), synced from the fetcher.
-- `dataset` is a stable key owned by a DayDataProvider (e.g. 'food.meals', 'food.targets').
-- New kinds of data only need a new provider + payload type - no migration.

CREATE TABLE day_data (
    dataset     TEXT        NOT NULL,
    day         DATE        NOT NULL,
    payload     JSONB       NOT NULL,
    synced_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (dataset, day)
);
