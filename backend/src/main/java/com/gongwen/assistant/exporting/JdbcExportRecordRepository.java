package com.gongwen.assistant.exporting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExportRecordRepository implements ExportRecordRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcExportRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ExportRecord record) {
        jdbcTemplate.update("""
                        insert into export_record (
                            template_name,
                            template_version,
                            file_name,
                            status,
                            error_code,
                            error_message
                        ) values (?, ?, ?, ?, ?, ?)
                        """,
                record.templateName(),
                record.templateVersion(),
                record.fileName(),
                record.status(),
                record.errorCode(),
                record.errorMessage());
    }
}
