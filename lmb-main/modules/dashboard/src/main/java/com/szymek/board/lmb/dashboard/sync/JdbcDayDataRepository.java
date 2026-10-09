package com.szymek.board.lmb.dashboard.sync;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcDayDataRepository implements DayDataRepository {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    JdbcDayDataRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void save(String dataset, LocalDate day, DayPayload payload, Instant syncedAt) {
        jdbc.sql("""
                        INSERT INTO day_data (dataset, day, payload, synced_at)
                        VALUES (:dataset, :day, CAST(:payload AS jsonb), :syncedAt)
                        ON CONFLICT (dataset, day) DO UPDATE
                        SET payload = EXCLUDED.payload, synced_at = EXCLUDED.synced_at
                        """)
                .param("dataset", dataset)
                .param("day", day)
                .param("payload", jsonMapper.writeValueAsString(payload))
                .param("syncedAt", OffsetDateTime.ofInstant(syncedAt, ZoneOffset.UTC))
                .update();
    }

    @Override
    public Map<LocalDate, Instant> findSyncedAt(String dataset, LocalDate from, LocalDate to) {
        Map<LocalDate, Instant> result = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT day, synced_at FROM day_data
                        WHERE dataset = :dataset AND day BETWEEN :from AND :to
                        """)
                .param("dataset", dataset)
                .param("from", from)
                .param("to", to)
                .query(rs -> {
                    result.put(rs.getObject("day", LocalDate.class), syncedAt(rs));
                });
        return result;
    }

    @Override
    public List<StoredDay> findBetween(
            String dataset, LocalDate from, LocalDate to, Class<? extends DayPayload> payloadType) {
        return jdbc.sql("""
                        SELECT day, payload, synced_at FROM day_data
                        WHERE dataset = :dataset AND day BETWEEN :from AND :to
                        ORDER BY day
                        """)
                .param("dataset", dataset)
                .param("from", from)
                .param("to", to)
                .query((rs, rowNum) -> new StoredDay(
                        rs.getObject("day", LocalDate.class),
                        jsonMapper.readValue(rs.getString("payload"), payloadType),
                        syncedAt(rs)))
                .list();
    }

    private static Instant syncedAt(ResultSet rs) throws SQLException {
        return rs.getObject("synced_at", OffsetDateTime.class).toInstant();
    }
}
