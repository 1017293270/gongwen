package com.gongwen.assistant.ai.candidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcAiParagraphCandidateRepositoryTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcAiParagraphCandidateRepository repository =
            new JdbcAiParagraphCandidateRepository(jdbcTemplate, new ObjectMapper());

    @Test
    void recordAndDtoPreservePointsAndNormalizeStatus() {
        Instant now = Instant.parse("2026-06-02T10:15:30Z");
        AiParagraphCandidate candidate = new AiParagraphCandidate(
                3L,
                9L,
                null,
                null,
                null,
                null,
                null,
                2,
                " Section one ",
                List.of("context", "actions"),
                null,
                "candidate text",
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now);

        AiParagraphCandidateDto dto = AiParagraphCandidateDto.from(candidate);

        assertThat(candidate.targetNodeRole()).isEmpty();
        assertThat(candidate.targetNodeTitle()).isEmpty();
        assertThat(candidate.status()).isEqualTo("PENDING");
        assertThat(candidate.candidateTextDigest()).isEmpty();
        assertThat(dto.points()).containsExactly("context", "actions");
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.heading()).isEqualTo("Section one");
    }

    @Test
    @SuppressWarnings("unchecked")
    void insertsListsUpdatesAcceptsAndDeletesCandidates() throws Exception {
        AiParagraphCandidate inserted = sampleCandidate(11L, 1, "PENDING", "draft", null);
        AiParagraphCandidate listedSecond = sampleCandidate(12L, 2, "PENDING", "second draft", null);
        AiParagraphCandidate completed = sampleCandidate(11L, 1, "COMPLETED", "final text", null);
        AiParagraphCandidate failed = sampleCandidate(11L, 1, "FAILED", "final text", null);
        AiParagraphCandidate accepted = sampleCandidate(11L, 1, "ACCEPTED", "final text", 5L);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(invocation -> List.of(map(invocation.getArgument(1), inserted)))
                .thenAnswer(invocation -> List.of(map(invocation.getArgument(1), inserted), map(invocation.getArgument(1), listedSecond)))
                .thenAnswer(invocation -> List.of(map(invocation.getArgument(1), inserted)))
                .thenAnswer(invocation -> List.of(map(invocation.getArgument(1), completed)))
                .thenAnswer(invocation -> List.of(map(invocation.getArgument(1), failed)))
                .thenAnswer(invocation -> List.of(map(invocation.getArgument(1), accepted)));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        AiParagraphCandidate created = repository.insert(inserted);
        List<AiParagraphCandidate> listed = repository.findByDraftId(7L);
        Optional<AiParagraphCandidate> found = repository.findById(11L);
        Optional<AiParagraphCandidate> textUpdated = repository.updateTextAndStatus(11L, "final text", "COMPLETED", "sha256");
        Optional<AiParagraphCandidate> errorUpdated = repository.updateStatusAndError(11L, "FAILED", "MODEL_TIMEOUT", "model timeout");
        Optional<AiParagraphCandidate> markedAccepted = repository.markAccepted(11L, completed.paragraphTraceId(), 5L);
        repository.delete(11L);

        assertThat(created.points()).containsExactly("context", "actions");
        assertThat(listed).extracting(AiParagraphCandidate::id).containsExactly(11L, 12L);
        assertThat(found).contains(inserted);
        assertThat(textUpdated).map(AiParagraphCandidate::status).contains("COMPLETED");
        assertThat(errorUpdated).map(AiParagraphCandidate::errorCode).contains("MODEL_TIMEOUT");
        assertThat(markedAccepted).map(AiParagraphCandidate::acceptedBy).contains(5L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), eq(7L));
        assertThat(sqlCaptor.getValue()).contains("order by section_index asc, id asc");
        verify(jdbcTemplate).update("delete from ai_paragraph_candidate where id = ?", 11L);
    }

    private AiParagraphCandidate sampleCandidate(long id, int sectionIndex, String status, String text, Long acceptedBy) {
        Instant createdAt = Instant.parse("2026-06-02T10:15:30Z");
        return new AiParagraphCandidate(
                id,
                7L,
                101L,
                "BODY",
                "Section one",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                sectionIndex,
                "I. Work plan",
                List.of("context", "actions"),
                "Generated from outline",
                text,
                "sha256",
                status,
                "FAILED".equals(status) ? "MODEL_TIMEOUT" : "",
                "FAILED".equals(status) ? "model timeout" : "",
                acceptedBy == null ? null : createdAt,
                acceptedBy,
                createdAt,
                createdAt);
    }

    private AiParagraphCandidate map(RowMapper<AiParagraphCandidate> mapper, AiParagraphCandidate candidate) throws Exception {
        return mapper.mapRow(resultSet(candidate), 0);
    }

    private ResultSet resultSet(AiParagraphCandidate candidate) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(candidate.id());
        when(rs.getLong("draft_id")).thenReturn(candidate.draftId());
        when(rs.getLong("target_node_id")).thenReturn(candidate.targetNodeId() == null ? 0L : candidate.targetNodeId());
        when(rs.getString("target_node_role")).thenReturn(candidate.targetNodeRole());
        when(rs.getString("target_node_title")).thenReturn(candidate.targetNodeTitle());
        when(rs.getObject("outline_trace_id", UUID.class)).thenReturn(candidate.outlineTraceId());
        when(rs.getObject("paragraph_trace_id", UUID.class)).thenReturn(candidate.paragraphTraceId());
        when(rs.getInt("section_index")).thenReturn(candidate.sectionIndex());
        when(rs.getString("heading")).thenReturn(candidate.heading());
        when(rs.getString("points_json")).thenReturn(new ObjectMapper().writeValueAsString(candidate.points()));
        when(rs.getString("instruction_summary")).thenReturn(candidate.instructionSummary());
        when(rs.getString("candidate_text")).thenReturn(candidate.candidateText());
        when(rs.getString("candidate_text_digest")).thenReturn(candidate.candidateTextDigest());
        when(rs.getString("status")).thenReturn(candidate.status());
        when(rs.getString("error_code")).thenReturn(candidate.errorCode());
        when(rs.getString("error_message")).thenReturn(candidate.errorMessage());
        when(rs.getObject("accepted_at", OffsetDateTime.class)).thenReturn(offset(candidate.acceptedAt()));
        when(rs.getLong("accepted_by")).thenReturn(candidate.acceptedBy() == null ? 0L : candidate.acceptedBy());
        when(rs.getObject("created_at", OffsetDateTime.class)).thenReturn(offset(candidate.createdAt()));
        when(rs.getObject("updated_at", OffsetDateTime.class)).thenReturn(offset(candidate.updatedAt()));
        when(rs.wasNull()).thenReturn(candidate.targetNodeId() == null, candidate.acceptedBy() == null);
        return rs;
    }

    private OffsetDateTime offset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
