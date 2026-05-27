package com.gongwen.assistant.template;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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
        List<TemplateSummary> inserted = jdbcTemplate.query("""
                        insert into document_template (template_name, version_no, document_type_code, status)
                        values (?, 1, ?, 'ACTIVE')
                        on conflict (template_name, version_no) do nothing
                        returning id, template_name, document_type_code, status
                        """,
                (rs, rowNum) -> new TemplateSummary(
                        rs.getLong("id"),
                        rs.getString("template_name"),
                        rs.getString("document_type_code"),
                        rs.getString("status")
                ),
                safeName,
                safeDocumentTypeCode);
        if (!inserted.isEmpty()) {
            return inserted.getFirst();
        }
        TemplateSummary existing = findByNameAndInitialVersion(safeName)
                .orElseThrow(() -> new TemplateException("TEMPLATE_CREATE_CONFLICT", "Template already exists but cannot be loaded"));
        if (existing.documentTypeCode() == null || existing.documentTypeCode().equals(safeDocumentTypeCode)) {
            return existing;
        }
        throw new TemplateException("TEMPLATE_NAME_ALREADY_EXISTS", "Template name already exists in another document type");
    }

    private Optional<TemplateSummary> findByNameAndInitialVersion(String templateName) {
        return jdbcTemplate.query("""
                        select id, template_name, document_type_code, status
                        from document_template
                        where template_name = ? and version_no = 1
                        """,
                (rs, rowNum) -> new TemplateSummary(
                        rs.getLong("id"),
                        rs.getString("template_name"),
                        rs.getString("document_type_code"),
                        rs.getString("status")
                ),
                templateName)
                .stream()
                .findFirst();
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

    @Override
    public Optional<TemplateSummary> findById(long id) {
        return jdbcTemplate.query("""
                        select id, template_name, document_type_code, status
                        from document_template
                        where id = ?
                        """,
                (rs, rowNum) -> new TemplateSummary(
                        rs.getLong("id"),
                        rs.getString("template_name"),
                        rs.getString("document_type_code"),
                        rs.getString("status")
                ),
                id)
                .stream()
                .findFirst();
    }
}
