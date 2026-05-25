package com.gongwen.assistant.document;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DocumentTypeRepository {
    private final JdbcTemplate jdbcTemplate;

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
                (rs, rowNum) -> new DocumentTypeDto(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getInt("sort_order")));
    }
}
