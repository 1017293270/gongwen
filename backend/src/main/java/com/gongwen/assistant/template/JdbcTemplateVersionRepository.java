package com.gongwen.assistant.template;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcTemplateVersionRepository implements TemplateVersionRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public TemplateVersion create(long templateId, String originalFileName, String contentType, long fileSizeBytes, String filePath) {
        jdbcTemplate.queryForList("select id from document_template where id = ? for update", templateId);
        int versionNo = nextVersionNo(templateId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into document_template_version
                        (template_id, version_no, original_file_name, content_type, file_size_bytes, file_path, parse_status)
                    values
                        (?, ?, ?, ?, ?, ?, 'PENDING')
                    """, new String[]{"id"});
            statement.setLong(1, templateId);
            statement.setInt(2, versionNo);
            statement.setString(3, originalFileName);
            statement.setString(4, contentType);
            statement.setLong(5, fileSizeBytes);
            statement.setString(6, filePath);
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue()).orElseThrow();
    }

    @Override
    public int nextVersionNo(long templateId) {
        Integer maxVersion = jdbcTemplate.queryForObject(
                "select coalesce(max(version_no), 0) from document_template_version where template_id = ?",
                Integer.class,
                templateId);
        return maxVersion + 1;
    }

    @Override
    public Optional<TemplateVersion> findById(long id) {
        return jdbcTemplate.query("select * from document_template_version where id = ?", this::mapRow, id)
                .stream()
                .findFirst();
    }

    @Override
    public void markParsed(long id, String profileHash) {
        jdbcTemplate.update("""
                update document_template_version
                set parse_status = 'READY',
                    profile_hash = ?,
                    parse_error_code = null,
                    parse_error_message = null
                where id = ?
                """, profileHash, id);
    }

    @Override
    public void markFailed(long id, String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                update document_template_version
                set parse_status = 'FAILED',
                    profile_hash = null,
                    parse_error_code = ?,
                    parse_error_message = ?
                where id = ?
                """, errorCode, errorMessage, id);
    }

    private TemplateVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TemplateVersion(
                rs.getLong("id"),
                rs.getLong("template_id"),
                rs.getInt("version_no"),
                rs.getString("original_file_name"),
                rs.getString("content_type"),
                rs.getLong("file_size_bytes"),
                rs.getString("file_path"),
                rs.getString("profile_hash"),
                rs.getString("parse_status"),
                rs.getString("parse_error_code"),
                rs.getString("parse_error_message"),
                rs.getObject("created_at", Instant.class)
        );
    }
}
