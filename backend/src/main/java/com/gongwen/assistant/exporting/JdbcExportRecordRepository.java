package com.gongwen.assistant.exporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongwen.assistant.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcExportRecordRepository implements ExportRecordRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcExportRecordRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new ObjectMapper());
    }

    @Autowired
    public JdbcExportRecordRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(ExportRecord record) {
        jdbcTemplate.update("""
                        insert into export_record (
                            template_id,
                            template_version_id,
                            template_name,
                            template_version,
                            draft_id,
                            exported_by,
                            department_id,
                            file_name,
                            file_path,
                            status,
                            error_code,
                            error_message,
                            export_strategy,
                            structure_mapping_profile_id,
                            structure_mapping_version,
                            structure_profile_snapshot_json,
                            mapping_profile_snapshot_json,
                            formatting_snapshot_json,
                            node_snapshot_json
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb))
                        """,
                record.templateId(),
                record.templateVersionId(),
                record.templateName(),
                record.templateVersion(),
                record.draftId(),
                record.exportedBy(),
                record.departmentId(),
                record.fileName(),
                record.filePath(),
                record.status(),
                record.errorCode(),
                record.errorMessage(),
                record.traceSnapshot().strategy(),
                record.traceSnapshot().structureMappingProfileId(),
                record.traceSnapshot().structureMappingVersion(),
                toJson(record.traceSnapshot().structureProfileSnapshot()),
                toJson(record.traceSnapshot().mappingProfileSnapshot()),
                toJson(record.traceSnapshot().formattingSnapshot()),
                toJson(record.traceSnapshot().nodeSnapshot()));
    }

    @Override
    public List<ExportRecordSummary> findAll(CurrentUser currentUser) {
        return jdbcTemplate.query("""
                        select er.id,
                               er.draft_id,
                               d.title as draft_title,
                               d.document_type_code,
                               er.template_id,
                               er.template_version_id,
                               er.template_name,
                               er.template_version,
                               er.file_name,
                               er.file_path,
                               er.status,
                               er.error_code,
                               er.error_message,
                               er.created_at
                        from export_record er
                        left join draft d on d.id = er.draft_id
                        where 1 = 1 %s
                        order by er.created_at desc, er.id desc
                        limit 100
                        """.formatted(visibilitySql(currentUser)),
                (rs, rowNum) -> new ExportRecordSummary(
                        rs.getLong("id"),
                        rs.getObject("draft_id") == null ? null : rs.getLong("draft_id"),
                        rs.getString("draft_title"),
                        rs.getString("document_type_code"),
                        rs.getObject("template_id") == null ? null : rs.getLong("template_id"),
                        rs.getObject("template_version_id") == null ? null : rs.getLong("template_version_id"),
                        rs.getString("template_name"),
                        rs.getInt("template_version"),
                        rs.getString("file_name"),
                        rs.getString("status"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        "SUCCESS".equals(rs.getString("status")) && rs.getString("file_path") != null,
                        "FAILED".equals(rs.getString("status")) && rs.getObject("draft_id") != null,
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                visibilityArgs(currentUser));
    }

    @Override
    public Optional<ExportRecordFileReference> findFileById(long recordId, CurrentUser currentUser) {
        List<ExportRecordFileReference> records = jdbcTemplate.query("""
                        select id, file_name, file_path
                        from export_record
                        where id = ?
                          and status = 'SUCCESS'
                          and file_path is not null %s
                        """.formatted(visibilitySql(currentUser)),
                (rs, rowNum) -> new ExportRecordFileReference(
                        rs.getLong("id"),
                        rs.getString("file_name"),
                        rs.getString("file_path")),
                queryArgs(recordId, currentUser));
        return records.stream().findFirst();
    }

    @Override
    public Optional<ExportRecordDetail> findDetailById(long recordId, CurrentUser currentUser) {
        List<ExportRecordDetail> records = jdbcTemplate.query("""
                        select er.id,
                               er.draft_id,
                               d.title as draft_title,
                               d.document_type_code,
                               er.template_id,
                               er.template_version_id,
                               er.template_name,
                               er.template_version,
                               er.file_name,
                               er.file_path,
                               er.status,
                               er.error_code,
                               er.error_message,
                               er.created_at
                        from export_record er
                        left join draft d on d.id = er.draft_id
                        where er.id = ? %s
                        """.formatted(visibilitySql(currentUser)),
                (rs, rowNum) -> new ExportRecordDetail(
                        rs.getLong("id"),
                        rs.getObject("draft_id") == null ? null : rs.getLong("draft_id"),
                        rs.getString("draft_title"),
                        rs.getString("document_type_code"),
                        rs.getObject("template_id") == null ? null : rs.getLong("template_id"),
                        rs.getObject("template_version_id") == null ? null : rs.getLong("template_version_id"),
                        rs.getString("template_name"),
                        rs.getInt("template_version"),
                        rs.getString("file_name"),
                        rs.getString("status"),
                        rs.getString("error_code"),
                        rs.getString("error_message"),
                        "SUCCESS".equals(rs.getString("status")) && rs.getString("file_path") != null,
                        "FAILED".equals(rs.getString("status")) && rs.getObject("draft_id") != null,
                        false,
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                queryArgs(recordId, currentUser));
        return records.stream().findFirst();
    }

    private String visibilitySql(CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return "";
        }
        return "and exported_by = ?";
    }

    private Object[] visibilityArgs(CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return new Object[]{};
        }
        return new Object[]{currentUser.id()};
    }

    private Object[] queryArgs(long recordId, CurrentUser currentUser) {
        if (currentUser == null || currentUser.systemAdmin()) {
            return new Object[]{recordId};
        }
        return new Object[]{recordId, currentUser.id()};
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize export trace snapshot", exception);
        }
    }
}
