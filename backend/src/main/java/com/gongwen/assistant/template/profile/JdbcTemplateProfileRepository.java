package com.gongwen.assistant.template.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class JdbcTemplateProfileRepository implements TemplateProfileRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcTemplateProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
        jdbcTemplate.update("""
                insert into template_profile (template_version_id, schema_version, profile_json)
                values (?, ?, cast(? as jsonb))
                on conflict (template_version_id)
                do update set schema_version = excluded.schema_version,
                              profile_json = excluded.profile_json,
                              created_at = now()
                """, templateVersionId, profile.schemaVersion(), toJson(profile));
    }

    @Override
    public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
        return jdbcTemplate.query(
                        "select profile_json from template_profile where template_version_id = ?",
                        this::mapProfile,
                        templateVersionId)
                .stream()
                .findFirst();
    }

    private TemplateProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        String json = rs.getString("profile_json");
        try {
            return objectMapper.readValue(json, TemplateProfile.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read template profile", exception);
        }
    }

    private String toJson(TemplateProfile profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize template profile", exception);
        }
    }
}
