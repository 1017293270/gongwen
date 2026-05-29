package com.gongwen.assistant.document;

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

    public DocumentTypeDto create(String code, String name, int sortOrder) {
        List<DocumentTypeDto> created = jdbcTemplate.query("""
                        insert into document_type (code, name, status, sort_order)
                        values (?, ?, 'ACTIVE', ?)
                        returning code, name, status, sort_order
                        """,
                rowMapper,
                code,
                name,
                sortOrder);
        if (created.isEmpty()) {
            throw new DocumentTypeException("DOCUMENT_TYPE_CREATE_FAILED", "Document type was not created");
        }
        return created.get(0);
    }

    public Optional<DocumentTypeDto> update(String code, String name, int sortOrder) {
        List<DocumentTypeDto> updated = jdbcTemplate.query("""
                        update document_type
                        set name = ?, sort_order = ?, updated_at = now()
                        where code = ?
                        returning code, name, status, sort_order
                        """,
                rowMapper,
                name,
                sortOrder,
                code);
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
        return jdbcTemplate.update("delete from document_type where code = ?", code) > 0;
    }
}
