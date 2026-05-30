package com.gongwen.assistant.organization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDepartmentRepository implements DepartmentRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDepartmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<DepartmentDto> findAll() {
        return jdbcTemplate.query("""
                        select id, parent_id, code, name, status, sort_order
                        from department
                        order by sort_order asc, id asc
                        """,
                (rs, rowNum) -> new DepartmentDto(
                        rs.getLong("id"),
                        rs.getObject("parent_id") == null ? null : rs.getLong("parent_id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getInt("sort_order"),
                        List.of()));
    }

    @Override
    public DepartmentDto create(Long parentId, String code, String name, int sortOrder) {
        List<DepartmentDto> created = jdbcTemplate.query("""
                        insert into department (parent_id, code, name, status, sort_order)
                        values (?, ?, ?, 'ACTIVE', ?)
                        returning id, parent_id, code, name, status, sort_order
                        """,
                (rs, rowNum) -> new DepartmentDto(
                        rs.getLong("id"),
                        rs.getObject("parent_id") == null ? null : rs.getLong("parent_id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getInt("sort_order"),
                        List.of()),
                parentId,
                code,
                name,
                sortOrder);
        if (created.isEmpty()) {
            throw new DepartmentException("DEPARTMENT_CREATE_FAILED", "Department was not created");
        }
        return created.getFirst();
    }

    @Override
    public Optional<DepartmentDto> update(long id, Long parentId, String name, String status, int sortOrder) {
        return jdbcTemplate.query("""
                        update department
                        set parent_id = ?, name = ?, status = ?, sort_order = ?, updated_at = now()
                        where id = ?
                        returning id, parent_id, code, name, status, sort_order
                        """,
                (rs, rowNum) -> new DepartmentDto(
                        rs.getLong("id"),
                        rs.getObject("parent_id") == null ? null : rs.getLong("parent_id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("status"),
                        rs.getInt("sort_order"),
                        List.of()),
                parentId,
                name,
                status,
                sortOrder,
                id)
                .stream()
                .findFirst();
    }

    @Override
    public int countChildren(long id) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from department where parent_id = ? and status = 'ACTIVE'", Integer.class, id);
        return count == null ? 0 : count;
    }

    @Override
    public int countUsers(long id) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from app_user where department_id = ? and status = 'ACTIVE'", Integer.class, id);
        return count == null ? 0 : count;
    }

    @Override
    public boolean disable(long id) {
        return jdbcTemplate.update("update department set status = 'DISABLED', updated_at = now() where id = ?", id) > 0;
    }
}
