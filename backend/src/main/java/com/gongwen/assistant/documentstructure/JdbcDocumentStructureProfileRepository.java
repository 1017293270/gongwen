package com.gongwen.assistant.documentstructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class JdbcDocumentStructureProfileRepository implements DocumentStructureProfileRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcDocumentStructureProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(long templateVersionId, DocumentStructureProfile profile) {
        jdbcTemplate.update("""
                insert into document_structure_profile (
                    template_version_id,
                    source_file_hash,
                    schema_version,
                    extractor_version,
                    node_count,
                    risk_count,
                    profile_json
                )
                values (?, ?, ?, ?, ?, ?, cast(? as jsonb))
                on conflict (template_version_id)
                do update set source_file_hash = excluded.source_file_hash,
                              schema_version = excluded.schema_version,
                              extractor_version = excluded.extractor_version,
                              node_count = excluded.node_count,
                              risk_count = excluded.risk_count,
                              profile_json = excluded.profile_json,
                              updated_at = now()
                """,
                templateVersionId,
                profile.sourceFileHash(),
                profile.schemaVersion(),
                profile.extractorVersion(),
                profile.nodes().size(),
                profile.risks().size(),
                toJson(profile));
    }

    @Override
    public Optional<DocumentStructureProfile> findByTemplateVersionId(long templateVersionId) {
        return jdbcTemplate.query(
                        "select profile_json from document_structure_profile where template_version_id = ?",
                        this::mapProfile,
                        templateVersionId)
                .stream()
                .findFirst();
    }

    private DocumentStructureProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        String json = rs.getString("profile_json");
        try {
            return objectMapper.readValue(json, DocumentStructureProfile.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read document structure profile", exception);
        }
    }

    private String toJson(DocumentStructureProfile profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize document structure profile", exception);
        }
    }
}
