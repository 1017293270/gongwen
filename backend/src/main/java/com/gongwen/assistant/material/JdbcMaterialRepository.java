package com.gongwen.assistant.material;

import com.gongwen.assistant.ai.MaterialPromptSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class JdbcMaterialRepository implements MaterialRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcMaterialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MaterialDto save(MaterialSaveCommand command) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into material (
                        draft_id,
                        original_file_name,
                        content_type,
                        file_size_bytes,
                        file_extension,
                        storage_path,
                        status,
                        extracted_text,
                        error_message
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, command.draftId());
            statement.setString(2, command.originalFileName());
            statement.setString(3, command.contentType());
            statement.setLong(4, command.fileSizeBytes());
            statement.setString(5, command.fileExtension());
            statement.setString(6, command.storagePath());
            statement.setString(7, command.status());
            statement.setString(8, command.extractedText());
            statement.setString(9, command.errorMessage());
            return statement;
        }, keyHolder);
        return command.toDto(keyHolder.getKey().longValue());
    }

    @Override
    public List<MaterialDto> findByDraftId(long draftId) {
        return jdbcTemplate.query("""
                        select id,
                               draft_id,
                               original_file_name,
                               content_type,
                               file_size_bytes,
                               file_extension,
                               status,
                               coalesce(length(extracted_text), 0) as extracted_text_length,
                               error_message
                        from material
                        where draft_id = ?
                        order by created_at desc, id desc
                        """,
                (rs, rowNum) -> new MaterialDto(
                        rs.getLong("id"),
                        rs.getLong("draft_id"),
                        rs.getString("original_file_name"),
                        rs.getString("content_type"),
                        rs.getLong("file_size_bytes"),
                        rs.getString("file_extension"),
                        rs.getString("status"),
                        rs.getInt("extracted_text_length"),
                        rs.getString("error_message")),
                draftId);
    }

    @Override
    public List<MaterialPromptSummary> findReadyTextSummariesByDraftId(long draftId) {
        return jdbcTemplate.query("""
                        select id,
                               original_file_name,
                               coalesce(extracted_text, '') as extracted_text
                        from material
                        where draft_id = ?
                          and status = 'READY'
                        order by created_at desc, id desc
                        """,
                (rs, rowNum) -> new MaterialPromptSummary(
                        rs.getLong("id"),
                        rs.getString("original_file_name"),
                        rs.getString("extracted_text")),
                draftId);
    }
}
