package com.gongwen.assistant.template;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

@Repository
public class JdbcTemplateRepository implements TemplateRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcTemplateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TemplateSummary create(String templateName, String documentTypeCode) {
        String safeName = templateName == null || templateName.isBlank() ? "未命名模板" : templateName.strip();
        String safeDocumentTypeCode = documentTypeCode == null || documentTypeCode.isBlank() ? "NOTICE" : documentTypeCode.strip();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into document_template (template_name, version_no, document_type_code, status)
                    values (?, 1, ?, 'ACTIVE')
                    """, new String[]{"id"});
            statement.setString(1, safeName);
            statement.setString(2, safeDocumentTypeCode);
            return statement;
        }, keyHolder);
        return new TemplateSummary(keyHolder.getKey().longValue(), safeName, safeDocumentTypeCode, "ACTIVE");
    }

    @Override
    public List<TemplateSummary> findAll(String documentTypeCode) {
        String safeDocumentTypeCode = documentTypeCode == null ? "" : documentTypeCode.strip();
        return jdbcTemplate.query("""
                        select id, template_name, document_type_code, status
                        from document_template
                        where ? = '' or document_type_code is null or document_type_code = ?
                        order by updated_at desc, id desc
                        """,
                (rs, rowNum) -> new TemplateSummary(
                        rs.getLong("id"),
                        rs.getString("template_name"),
                        rs.getString("document_type_code"),
                        rs.getString("status")
                ),
                safeDocumentTypeCode,
                safeDocumentTypeCode);
    }
}
