package com.gongwen.assistant.draft.node;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDraftNodeRepository implements DraftNodeRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDraftNodeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DraftNode> findByDraftId(long draftId) {
        return jdbcTemplate.query("""
                        select *
                        from draft_node
                        where draft_id = ?
                        order by sort_order asc, id asc
                        """,
                this::mapRow,
                draftId);
    }

    @Override
    public boolean existsByDraftId(long draftId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from draft_node where draft_id = ?",
                Integer.class,
                draftId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public List<DraftNode> replaceForDraft(long draftId, List<DraftNode> nodes) {
        jdbcTemplate.update("delete from draft_node where draft_id = ?", draftId);
        for (DraftNode node : nodes) {
            jdbcTemplate.update("""
                            insert into draft_node (
                                draft_id,
                                structure_mapping_profile_id,
                                template_node_key,
                                parent_template_node_key,
                                node_type,
                                role,
                                slot_key,
                                title,
                                content,
                                sort_order,
                                status,
                                format_override_json
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                            """,
                    draftId,
                    node.structureMappingProfileId(),
                    node.templateNodeKey(),
                    node.parentTemplateNodeKey(),
                    node.nodeType(),
                    node.role(),
                    node.slotKey(),
                    node.title(),
                    node.content(),
                    node.sortOrder(),
                    node.status(),
                    toJson(node.formatOverride()));
        }
        return findByDraftId(draftId);
    }

    @Override
    public Optional<DraftNode> updateContent(long draftId, long nodeId, String content, String status) {
        int updated = jdbcTemplate.update("""
                update draft_node
                set content = ?,
                    status = ?,
                    updated_at = now()
                where draft_id = ?
                  and id = ?
                """, content, status, draftId, nodeId);
        if (updated == 0) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        select *
                        from draft_node
                        where draft_id = ?
                          and id = ?
                        """,
                        this::mapRow,
                        draftId,
                        nodeId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<DraftNode> updateFormatOverride(long draftId, long nodeId, DraftNodeFormatOverride override, String status) {
        int updated = jdbcTemplate.update("""
                update draft_node
                set format_override_json = cast(? as jsonb),
                    status = ?,
                    updated_at = now()
                where draft_id = ?
                  and id = ?
                """, toJson(override), status, draftId, nodeId);
        if (updated == 0) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        select *
                        from draft_node
                        where draft_id = ?
                          and id = ?
                        """,
                        this::mapRow,
                        draftId,
                        nodeId)
                .stream()
                .findFirst();
    }

    private DraftNode mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DraftNode(
                rs.getLong("id"),
                rs.getLong("draft_id"),
                rs.getObject("structure_mapping_profile_id") == null ? null : rs.getLong("structure_mapping_profile_id"),
                rs.getString("template_node_key"),
                rs.getString("parent_template_node_key"),
                rs.getString("node_type"),
                rs.getString("role"),
                rs.getString("slot_key"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getInt("sort_order"),
                rs.getString("status"),
                readFormatOverride(rs.getString("format_override_json")),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }

    private DraftNodeFormatOverride readFormatOverride(String json) {
        if (json == null || json.isBlank()) {
            return DraftNodeFormatOverride.empty();
        }
        try {
            return objectMapper.readValue(json, DraftNodeFormatOverride.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read draft node format override", exception);
        }
    }

    private String toJson(DraftNodeFormatOverride override) {
        try {
            return objectMapper.writeValueAsString(override == null ? DraftNodeFormatOverride.empty() : override);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize draft node format override", exception);
        }
    }
}
