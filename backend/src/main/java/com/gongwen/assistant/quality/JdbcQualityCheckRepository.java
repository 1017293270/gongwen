package com.gongwen.assistant.quality;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcQualityCheckRepository implements QualityCheckRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcQualityCheckRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(QualityCheckRecord record) {
        jdbcTemplate.update("""
                insert into quality_check_result (
                    id, draft_id, status, export_blocked, result_json, ai_trace_id
                ) values (?, ?, ?, ?, cast(? as jsonb), ?)
                """,
                record.id(),
                record.draftId(),
                record.status(),
                record.exportBlocked(),
                toJson(record.result()),
                record.aiTraceId());
    }

    @Override
    public Optional<QualityCheckResponse> findLatestByDraftId(long draftId) {
        return jdbcTemplate.query("""
                        select result_json
                        from quality_check_result
                        where draft_id = ?
                        order by created_at desc
                        limit 1
                        """,
                (rs, rowNum) -> mapResponse(rs),
                draftId)
                .stream()
                .findFirst();
    }

    private QualityCheckResponse mapResponse(ResultSet rs) throws SQLException {
        try {
            return objectMapper.readValue(rs.getString("result_json"), QualityCheckResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Quality check result JSON is invalid", exception);
        }
    }

    private String toJson(QualityCheckResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Quality check result JSON is invalid", exception);
        }
    }
}
