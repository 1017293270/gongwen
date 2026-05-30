package com.gongwen.assistant.template;

import com.gongwen.assistant.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
        return create(templateName, documentTypeCode, null);
    }

    @Override
    public TemplateSummary create(String templateName, String documentTypeCode, CurrentUser currentUser) {
        String safeName = templateName == null || templateName.isBlank() ? "未命名模板" : templateName.strip();
        String safeDocumentTypeCode = documentTypeCode == null || documentTypeCode.isBlank()
                ? "NOTICE"
                : documentTypeCode.strip();
        List<TemplateSummary> inserted = jdbcTemplate.query("""
                        insert into document_template (template_name, version_no, document_type_code, status, created_by, department_id)
                        values (?, 1, ?, 'ACTIVE', ?, ?)
                        on conflict (template_name, version_no) do nothing
                        returning id, template_name, document_type_code, status
                        """,
                this::mapSummary,
                safeName,
                safeDocumentTypeCode,
                currentUser == null ? null : currentUser.id(),
                currentUser == null ? null : currentUser.departmentId());
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
                this::mapSummary,
                templateName)
                .stream()
                .findFirst();
    }

    @Override
    public List<TemplateSummary> findAll(String documentTypeCode) {
        return findAll(documentTypeCode, null);
    }

    @Override
    public List<TemplateSummary> findAll(String documentTypeCode, CurrentUser currentUser) {
        String safeDocumentTypeCode = documentTypeCode == null ? "" : documentTypeCode.strip();
        return jdbcTemplate.query("""
                        select id, template_name, document_type_code, status
                        from document_template
                        where (? = '' or document_type_code is null or document_type_code = ?)
                          %s
                        order by updated_at desc, id desc
                        """.formatted(visibilitySql(currentUser)),
                this::mapSummary,
                findAllArgs(safeDocumentTypeCode, currentUser));
    }

    @Override
    public Optional<TemplateSummary> findById(long id) {
        return findById(id, null);
    }

    @Override
    public Optional<TemplateSummary> findById(long id, CurrentUser currentUser) {
        return jdbcTemplate.query("""
                        select id, template_name, document_type_code, status
                        from document_template
                        where id = ? %s
                        """.formatted(visibilitySql(currentUser)),
                this::mapSummary,
                queryArgs(id, currentUser))
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public void deleteById(long id) {
        deleteById(id, null);
    }

    @Override
    @Transactional
    public void deleteById(long id, CurrentUser currentUser) {
        findById(id, currentUser)
                .orElseThrow(() -> new TemplateException("TEMPLATE_NOT_FOUND", "Template not found"));
        jdbcTemplate.update("""
                update draft
                set template_version_id = null, updated_at = now()
                where template_version_id in (
                    select id from document_template_version where template_id = ?
                )
                """, id);
        jdbcTemplate.update("delete from document_template where id = ?", id);
    }

    private TemplateSummary mapSummary(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new TemplateSummary(
                rs.getLong("id"),
                rs.getString("template_name"),
                rs.getString("document_type_code"),
                rs.getString("status"));
    }

    private String visibilitySql(CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return "";
        }
        return "and (created_by is null or created_by = ?)";
    }

    private Object[] findAllArgs(String documentTypeCode, CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return new Object[]{documentTypeCode, documentTypeCode};
        }
        return new Object[]{documentTypeCode, documentTypeCode, currentUser.id()};
    }

    private Object[] queryArgs(long id, CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return new Object[]{id};
        }
        return new Object[]{id, currentUser.id()};
    }
}
