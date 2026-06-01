package com.gongwen.assistant.rendering;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class JdbcDocumentRenderPreviewRepository implements DocumentRenderPreviewRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDocumentRenderPreviewRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public DocumentRenderPreview create(DocumentRenderPreview preview) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into document_render_preview (
                        draft_id,
                        template_version_id,
                        source_file_hash,
                        renderer,
                        renderer_version,
                        status,
                        page_count,
                        storage_path,
                        manifest_json,
                        error_code,
                        error_message
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
                    """, new String[]{"id"});
            if (preview.draftId() == null) {
                statement.setObject(1, null);
            } else {
                statement.setLong(1, preview.draftId());
            }
            statement.setLong(2, preview.templateVersionId());
            statement.setString(3, preview.sourceFileHash());
            statement.setString(4, preview.renderer());
            statement.setString(5, preview.rendererVersion());
            statement.setString(6, preview.status().name());
            statement.setInt(7, preview.pageCount());
            statement.setString(8, preview.storagePath());
            statement.setString(9, toJson(preview.manifest()));
            statement.setString(10, preview.errorCode());
            statement.setString(11, preview.errorMessage());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public void updateResult(
            long id,
            DocumentRenderPreviewStatus status,
            int pageCount,
            String storagePath,
            DocumentRenderPreviewManifest manifest,
            String rendererVersion,
            String errorCode,
            String errorMessage
    ) {
        jdbcTemplate.update("""
                update document_render_preview
                set status = ?,
                    page_count = ?,
                    storage_path = ?,
                    manifest_json = cast(? as jsonb),
                    renderer_version = ?,
                    error_code = ?,
                    error_message = ?,
                    updated_at = now()
                where id = ?
                """,
                status.name(),
                pageCount,
                storagePath,
                toJson(manifest),
                rendererVersion,
                errorCode,
                errorMessage,
                id);
    }

    @Override
    public Optional<DocumentRenderPreview> findLatestByTemplateVersionId(long templateVersionId) {
        return jdbcTemplate.query("""
                        select *
                        from document_render_preview
                        where template_version_id = ?
                          and draft_id is null
                        order by created_at desc, id desc
                        limit 1
                        """,
                        this::mapRow,
                        templateVersionId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<DocumentRenderPreview> findLatestByDraftId(long draftId) {
        return jdbcTemplate.query("""
                        select *
                        from document_render_preview
                        where draft_id = ?
                        order by created_at desc, id desc
                        limit 1
                        """,
                        this::mapRow,
                        draftId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<DocumentRenderPreview> findById(long id) {
        return jdbcTemplate.query("select * from document_render_preview where id = ?", this::mapRow, id)
                .stream()
                .findFirst();
    }

    private DocumentRenderPreview mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new DocumentRenderPreview(
                rs.getLong("id"),
                nullableLong(rs, "draft_id"),
                rs.getLong("template_version_id"),
                rs.getString("source_file_hash"),
                rs.getString("renderer"),
                rs.getString("renderer_version"),
                DocumentRenderPreviewStatus.valueOf(rs.getString("status")),
                rs.getInt("page_count"),
                rs.getString("storage_path"),
                fromJson(rs.getString("manifest_json")),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private DocumentRenderPreviewManifest fromJson(String json) {
        try {
            return objectMapper.readValue(json, DocumentRenderPreviewManifest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read document render preview manifest", exception);
        }
    }

    private String toJson(DocumentRenderPreviewManifest manifest) {
        try {
            return objectMapper.writeValueAsString(manifest == null ? DocumentRenderPreviewManifest.empty() : manifest);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize document render preview manifest", exception);
        }
    }
}
