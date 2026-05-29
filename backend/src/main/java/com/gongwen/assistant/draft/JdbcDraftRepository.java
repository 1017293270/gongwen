package com.gongwen.assistant.draft;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcDraftRepository implements DraftRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into draft (document_type_code, title, status)
                    values (?, ?, 'DRAFT')
                    """, new String[]{"id"});
            statement.setString(1, documentTypeCode);
            statement.setString(2, title);
            return statement;
        }, keyHolder);
        long draftId = keyHolder.getKey().longValue();
        insertBlocks(draftId, blocks);
        return findById(draftId);
    }

    @Override
    public DraftDetailDto findById(long id) {
        List<Map<String, Object>> drafts = jdbcTemplate.queryForList("""
                select id, document_type_code, title, status, template_version_id
                from draft
                where id = ?
                """, id);
        if (drafts.isEmpty()) {
            throw new DraftNotFoundException(id);
        }
        Map<String, Object> draft = drafts.get(0);
        return new DraftDetailDto(
                ((Number) draft.get("id")).longValue(),
                (String) draft.get("document_type_code"),
                (String) draft.get("title"),
                (String) draft.get("status"),
                draft.get("template_version_id") == null ? null : ((Number) draft.get("template_version_id")).longValue(),
                findBlocks(id));
    }

    @Override
    public List<DraftSummaryDto> listByDocumentType(String documentTypeCode) {
        return jdbcTemplate.query("""
                        select id, document_type_code, title, status, template_version_id, updated_at
                        from draft
                        where document_type_code = ?
                        order by updated_at desc, id desc
                        """,
                (rs, rowNum) -> new DraftSummaryDto(
                        rs.getLong("id"),
                        rs.getString("document_type_code"),
                        rs.getString("title"),
                        rs.getString("status"),
                        rs.getObject("template_version_id") == null ? null : rs.getLong("template_version_id"),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant().toString()),
                documentTypeCode);
    }

    @Override
    @Transactional
    public DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks) {
        findById(id);
        String title = blocks.stream()
                .filter(block -> "TITLE".equals(block.blockType()))
                .findFirst()
                .map(DraftBlockUpdateRequest::content)
                .orElse("");
        jdbcTemplate.update("update draft set title = ?, updated_at = now() where id = ?", title, id);
        jdbcTemplate.update("delete from draft_block where draft_id = ?", id);
        insertBlocks(id, blocks);
        return findById(id);
    }

    @Override
    public DraftDetailDto updateTitle(long id, String title) {
        findById(id);
        jdbcTemplate.update("update draft set title = ?, updated_at = now() where id = ?", title, id);
        return findById(id);
    }

    @Override
    public DraftDetailDto updateTemplateVersion(long id, Long templateVersionId) {
        findById(id);
        jdbcTemplate.update("update draft set template_version_id = ?, updated_at = now() where id = ?", templateVersionId, id);
        return findById(id);
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        findById(id);
        jdbcTemplate.update("delete from quality_check_result where draft_id = ?", id);
        jdbcTemplate.update("delete from ai_generation_trace where draft_id = ?", id);
        jdbcTemplate.update("delete from draft where id = ?", id);
    }

    private void insertBlocks(long draftId, List<DraftBlockUpdateRequest> blocks) {
        for (DraftBlockUpdateRequest block : blocks) {
            jdbcTemplate.update("""
                    insert into draft_block (draft_id, block_type, content, sort_order)
                    values (?, ?, ?, ?)
                    """, draftId, block.blockType(), block.content(), block.sortOrder());
        }
    }

    private List<DraftBlockDto> findBlocks(long draftId) {
        return jdbcTemplate.query("""
                        select id, block_type, content, sort_order
                        from draft_block
                        where draft_id = ?
                        order by sort_order asc, id asc
                        """,
                (rs, rowNum) -> new DraftBlockDto(
                        rs.getLong("id"),
                        rs.getString("block_type"),
                        rs.getString("content"),
                        rs.getInt("sort_order")),
                draftId);
    }
}
