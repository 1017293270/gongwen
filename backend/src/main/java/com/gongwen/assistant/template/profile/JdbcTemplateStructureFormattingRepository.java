package com.gongwen.assistant.template.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongwen.assistant.template.TemplateException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class JdbcTemplateStructureFormattingRepository implements TemplateStructureFormattingRepository {
    private static final String TARGET_TYPE = "STRUCTURE";
    private static final String RULE_TYPE = "STRUCTURE_FORMATTING_OVERRIDE";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcTemplateStructureFormattingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId) {
        Map<String, TemplateStructureFormattingProfile> overrides = new LinkedHashMap<>();
        jdbcTemplate.query("""
                        select target_key, expected_value
                        from template_rule
                        where template_version_id = ?
                          and target_type = ?
                          and rule_type = ?
                          and enabled = true
                        order by id asc
                        """,
                (rs) -> {
                    overrides.put(rs.getString("target_key"), mapFormatting(rs));
                },
                templateVersionId,
                TARGET_TYPE,
                RULE_TYPE);
        return overrides;
    }

    @Override
    public void saveOverride(long templateVersionId, String structureKey, TemplateStructureFormattingProfile formatting) {
        if (structureKey == null || structureKey.isBlank()) {
            throw new TemplateException("TEMPLATE_STRUCTURE_KEY_REQUIRED", "Template structure key is required");
        }
        jdbcTemplate.update("""
                        update template_rule
                        set expected_value = cast(? as jsonb),
                            source = 'ADMIN',
                            enabled = true,
                            updated_at = now()
                        where template_version_id = ?
                          and target_type = ?
                          and target_key = ?
                          and rule_type = ?
                        """,
                toJson(formatting),
                templateVersionId,
                TARGET_TYPE,
                structureKey,
                RULE_TYPE);
        jdbcTemplate.update("""
                        insert into template_rule
                            (template_version_id, target_type, target_key, rule_type, expected_value, severity, source, enabled)
                        select ?, ?, ?, ?, cast(? as jsonb), 'INFO', 'ADMIN', true
                        where not exists (
                            select 1
                            from template_rule
                            where template_version_id = ?
                              and target_type = ?
                              and target_key = ?
                              and rule_type = ?
                        )
                        """,
                templateVersionId,
                TARGET_TYPE,
                structureKey,
                RULE_TYPE,
                toJson(formatting),
                templateVersionId,
                TARGET_TYPE,
                structureKey,
                RULE_TYPE);
    }

    private TemplateStructureFormattingProfile mapFormatting(ResultSet rs) throws SQLException {
        try {
            return objectMapper.readValue(rs.getString("expected_value"), TemplateStructureFormattingProfile.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read template structure formatting override", exception);
        }
    }

    private String toJson(TemplateStructureFormattingProfile formatting) {
        try {
            return objectMapper.writeValueAsString(formatting);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize template structure formatting override", exception);
        }
    }
}
