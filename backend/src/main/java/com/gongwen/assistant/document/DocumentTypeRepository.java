package com.gongwen.assistant.document;

import com.gongwen.assistant.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DocumentTypeRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<DocumentTypeDto> rowMapper = (rs, rowNum) -> new DocumentTypeDto(
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("status"),
            rs.getInt("sort_order"));

    public DocumentTypeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DocumentTypeDto> findActive() {
        return jdbcTemplate.query("""
                        select code, name, status, sort_order
                        from document_type
                        where status = 'ACTIVE'
                        order by sort_order asc
                        """,
                rowMapper);
    }

    public List<DocumentTypeDto> findVisible(CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return findActive();
        }
        return jdbcTemplate.query("""
                        select code, name, status, sort_order
                        from document_type
                        where status = 'ACTIVE'
                          and (created_by is null or created_by = ?)
                        order by sort_order asc
                        """,
                rowMapper,
                currentUser.id());
    }

    public DocumentTypeDto create(String code, String name, int sortOrder) {
        return create(code, name, sortOrder, null);
    }

    public DocumentTypeDto create(String code, String name, int sortOrder, CurrentUser currentUser) {
        List<DocumentTypeDto> created = jdbcTemplate.query("""
                        insert into document_type (code, name, status, sort_order, created_by, department_id)
                        values (?, ?, 'ACTIVE', ?, ?, ?)
                        returning code, name, status, sort_order
                        """,
                rowMapper,
                code,
                name,
                sortOrder,
                currentUser == null ? null : currentUser.id(),
                currentUser == null ? null : currentUser.departmentId());
        if (created.isEmpty()) {
            throw new DocumentTypeException("DOCUMENT_TYPE_CREATE_FAILED", "Document type was not created");
        }
        return created.get(0);
    }

    public Optional<DocumentTypeDto> update(String code, String name, int sortOrder) {
        return update(code, name, sortOrder, null);
    }

    public Optional<DocumentTypeDto> update(String code, String name, int sortOrder, CurrentUser currentUser) {
        List<DocumentTypeDto> updated = jdbcTemplate.query("""
                        update document_type
                        set name = ?, sort_order = ?, updated_at = now()
                        where code = ? %s
                        returning code, name, status, sort_order
                        """.formatted(ownershipSql(currentUser)),
                rowMapper,
                updateArgs(name, sortOrder, code, currentUser));
        return updated.stream().findFirst();
    }

    public int countDrafts(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from draft where document_type_code = ?",
                Integer.class,
                code);
        return count == null ? 0 : count;
    }

    public int countTemplates(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from document_template where document_type_code = ?",
                Integer.class,
                code);
        return count == null ? 0 : count;
    }

    public boolean delete(String code) {
        return delete(code, null);
    }

    public boolean delete(String code, CurrentUser currentUser) {
        return jdbcTemplate.update(
                "delete from document_type where code = ? " + ownershipSql(currentUser),
                queryArgs(code, currentUser)) > 0;
    }

    private String ownershipSql(CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return "";
        }
        return "and created_by = ?";
    }

    private Object[] queryArgs(String code, CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return new Object[]{code};
        }
        return new Object[]{code, currentUser.id()};
    }

    private Object[] updateArgs(String name, int sortOrder, String code, CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return new Object[]{name, sortOrder, code};
        }
        return new Object[]{name, sortOrder, code, currentUser.id()};
    }
}
