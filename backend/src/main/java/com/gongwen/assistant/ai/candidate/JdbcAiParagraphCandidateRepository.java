package com.gongwen.assistant.ai.candidate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAiParagraphCandidateRepository implements AiParagraphCandidateRepository {
    private static final TypeReference<List<String>> POINTS_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAiParagraphCandidateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiParagraphCandidate insert(AiParagraphCandidate candidate) {
        return jdbcTemplate.query("""
                        insert into ai_paragraph_candidate (
                            draft_id,
                            target_node_id,
                            target_node_role,
                            target_node_title,
                            outline_trace_id,
                            paragraph_trace_id,
                            section_index,
                            heading,
                            points_json,
                            instruction_summary,
                            candidate_text,
                            candidate_text_digest,
                            status,
                            error_code,
                            error_message,
                            accepted_at,
                            accepted_by
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, ?, ?)
                        returning *
                        """,
                this::mapRow,
                candidate.draftId(),
                candidate.targetNodeId(),
                candidate.targetNodeRole(),
                candidate.targetNodeTitle(),
                candidate.outlineTraceId(),
                candidate.paragraphTraceId(),
                candidate.sectionIndex(),
                candidate.heading(),
                toJson(candidate.points()),
                candidate.instructionSummary(),
                candidate.candidateText(),
                candidate.candidateTextDigest(),
                candidate.status(),
                candidate.errorCode(),
                candidate.errorMessage(),
                candidate.acceptedAt() == null ? null : candidate.acceptedAt().atOffset(ZoneOffset.UTC),
                candidate.acceptedBy())
                .stream()
                .findFirst()
                .orElseThrow();
    }

    @Override
    public List<AiParagraphCandidate> findByDraftId(long draftId) {
        return jdbcTemplate.query("""
                        select *
                        from ai_paragraph_candidate
                        where draft_id = ?
                        order by section_index asc, id asc
                        """,
                this::mapRow,
                draftId);
    }

    @Override
    public Optional<AiParagraphCandidate> findById(long candidateId) {
        return jdbcTemplate.query("""
                        select *
                        from ai_paragraph_candidate
                        where id = ?
                        """,
                this::mapRow,
                candidateId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AiParagraphCandidate> updateTextAndStatus(long candidateId, String text, String status, String digest) {
        int updated = jdbcTemplate.update("""
                update ai_paragraph_candidate
                set candidate_text = ?,
                    candidate_text_digest = ?,
                    status = ?,
                    error_code = '',
                    error_message = '',
                    updated_at = now()
                where id = ?
                """,
                text == null ? "" : text,
                normalize(digest),
                normalizeStatus(status),
                candidateId);
        return updated == 0 ? Optional.empty() : findById(candidateId);
    }

    @Override
    public Optional<AiParagraphCandidate> updateStatusAndError(long candidateId, String status, String errorCode, String errorMessage) {
        int updated = jdbcTemplate.update("""
                update ai_paragraph_candidate
                set status = ?,
                    error_code = ?,
                    error_message = ?,
                    updated_at = now()
                where id = ?
                """,
                normalizeStatus(status),
                normalize(errorCode),
                normalize(errorMessage),
                candidateId);
        return updated == 0 ? Optional.empty() : findById(candidateId);
    }

    @Override
    public Optional<AiParagraphCandidate> markAccepted(long candidateId, UUID paragraphTraceId, long acceptedBy) {
        int updated = jdbcTemplate.update("""
                update ai_paragraph_candidate
                set paragraph_trace_id = ?,
                    status = 'ACCEPTED',
                    accepted_at = now(),
                    accepted_by = ?,
                    updated_at = now()
                where id = ?
                """,
                paragraphTraceId,
                acceptedBy,
                candidateId);
        return updated == 0 ? Optional.empty() : findById(candidateId);
    }

    @Override
    public void discardUnacceptedByDraftId(long draftId, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                update ai_paragraph_candidate
                set status = 'DISCARDED',
                    error_code = ?,
                    error_message = ?,
                    updated_at = now()
                where draft_id = ?
                  and status <> 'ACCEPTED'
                """,
                normalize(errorCode),
                normalize(errorMessage),
                draftId);
    }

    @Override
    public void delete(long candidateId) {
        jdbcTemplate.update("delete from ai_paragraph_candidate where id = ?", candidateId);
    }

    private AiParagraphCandidate mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AiParagraphCandidate(
                rs.getLong("id"),
                rs.getLong("draft_id"),
                nullableLong(rs, "target_node_id"),
                rs.getString("target_node_role"),
                rs.getString("target_node_title"),
                rs.getObject("outline_trace_id", UUID.class),
                rs.getObject("paragraph_trace_id", UUID.class),
                rs.getInt("section_index"),
                rs.getString("heading"),
                fromJson(rs.getString("points_json")),
                rs.getString("instruction_summary"),
                rs.getString("candidate_text"),
                rs.getString("candidate_text_digest"),
                rs.getString("status"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                toInstant(rs.getObject("accepted_at", OffsetDateTime.class)),
                nullableLong(rs, "accepted_by"),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                toInstant(rs.getObject("updated_at", OffsetDateTime.class))
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, POINTS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read paragraph candidate points", exception);
        }
    }

    private String toJson(List<String> points) {
        try {
            return objectMapper.writeValueAsString(points == null ? List.of() : points);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize paragraph candidate points", exception);
        }
    }

    private static String normalizeStatus(String value) {
        return value == null || value.isBlank() ? "PENDING" : value.strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
