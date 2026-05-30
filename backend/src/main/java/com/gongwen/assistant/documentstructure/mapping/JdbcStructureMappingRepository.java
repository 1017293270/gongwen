package com.gongwen.assistant.documentstructure.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongwen.assistant.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStructureMappingRepository implements StructureMappingRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcStructureMappingRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public StructureMappingProfile save(StructureMappingProfile profile, CurrentUser currentUser) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into structure_mapping_profile (
                        template_version_id,
                        version_no,
                        status,
                        mapping_json,
                        validation_json,
                        created_by,
                        department_id,
                        published_at
                    )
                    values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, profile.templateVersionId());
            statement.setInt(2, profile.versionNo());
            statement.setString(3, profile.status());
            statement.setString(4, toJson(profile.items()));
            statement.setString(5, toJson(profile.validationItems()));
            if (currentUser == null) {
                statement.setObject(6, null);
                statement.setObject(7, null);
            } else {
                statement.setLong(6, currentUser.id());
                statement.setObject(7, currentUser.departmentId());
            }
            statement.setObject(8, profile.publishedAt() == null ? null : OffsetDateTime.parse(profile.publishedAt().toString()));
            return statement;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<StructureMappingProfile> findLatest(long templateVersionId) {
        return jdbcTemplate.query("""
                        select *
                        from structure_mapping_profile
                        where template_version_id = ?
                        order by version_no desc, id desc
                        limit 1
                        """,
                        this::mapRow,
                        templateVersionId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<StructureMappingProfile> findLatestByStatus(long templateVersionId, String status) {
        return jdbcTemplate.query("""
                        select *
                        from structure_mapping_profile
                        where template_version_id = ?
                          and status = ?
                        order by version_no desc, id desc
                        limit 1
                        """,
                        this::mapRow,
                        templateVersionId,
                        status)
                .stream()
                .findFirst();
    }

    @Override
    public int nextVersionNo(long templateVersionId) {
        Integer maxVersion = jdbcTemplate.queryForObject(
                "select coalesce(max(version_no), 0) from structure_mapping_profile where template_version_id = ?",
                Integer.class,
                templateVersionId);
        return maxVersion + 1;
    }

    private Optional<StructureMappingProfile> findById(long id) {
        return jdbcTemplate.query("select * from structure_mapping_profile where id = ?", this::mapRow, id)
                .stream()
                .findFirst();
    }

    private StructureMappingProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new StructureMappingProfile(
                rs.getLong("id"),
                rs.getLong("template_version_id"),
                rs.getInt("version_no"),
                rs.getString("status"),
                readItems(rs.getString("mapping_json")),
                readValidationItems(rs.getString("validation_json")),
                0,
                0,
                rs.getObject("published_at", OffsetDateTime.class) == null
                        ? null
                        : rs.getObject("published_at", OffsetDateTime.class).toInstant(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }

    private List<StructureMappingItem> readItems(String json) {
        try {
            return objectMapper.readerForListOf(StructureMappingItem.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read structure mapping items", exception);
        }
    }

    private List<StructureMappingValidationItem> readValidationItems(String json) {
        try {
            return objectMapper.readerForListOf(StructureMappingValidationItem.class).readValue(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read structure mapping validation items", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize structure mapping payload", exception);
        }
    }
}
