package com.example.main.core;


import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class GenericOutboxRepository {

    private final JdbcTemplate jdbc;
    private final String tableName;
    private final String pendingStatusCol = "status";
    private final String payloadCol = "payload";
    private final String idCol = "id";

    public GenericOutboxRepository(JdbcTemplate jdbc, String tableName) {
        this.jdbc = jdbc;
        this.tableName = tableName;
    }

    /**
     * Fetch one pending record and lock it (to avoid double processing).
     * Uses FOR UPDATE SKIP LOCKED — works in recent MySQL/Postgres + others.
     * Annotated transactional so lock applies.
     */
    @Transactional
    public OutboxRecord fetchNextPending(String pendingStatus) {
        String sql = "SELECT " + idCol + ", " + payloadCol +
                     " FROM " + tableName +
                     " WHERE " + pendingStatusCol + " = ? " +
                     " ORDER BY " + idCol +
                     " LIMIT 1 FOR UPDATE SKIP LOCKED";

        List<OutboxRecord> rows = jdbc.query(sql, new Object[]{pendingStatus}, (rs, i) -> {
//            UUID id = rs.getString(idCol);
            UUID id = UUID.fromString(rs.getString(idCol));
            String payload = rs.getString(payloadCol);
            return new OutboxRecord(id, payload);
        });

        return rows.isEmpty() ? null : rows.get(0);
    }

    public void updateStatus(UUID id, String status) {
        String sql = "UPDATE " + tableName + " SET " + pendingStatusCol + " = ? WHERE " + idCol + " = ?";
        jdbc.update(sql, status, id);
    }
}
