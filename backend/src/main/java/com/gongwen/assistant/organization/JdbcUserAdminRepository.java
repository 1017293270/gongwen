package com.gongwen.assistant.organization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserAdminRepository implements UserAdminRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UserAdminDto> findAll() {
        return jdbcTemplate.query("""
                        select u.id, u.username, u.display_name, u.department_id, d.name as department_name, u.status
                        from app_user u
                        left join department d on d.id = u.department_id
                        order by u.updated_at desc, u.id desc
                        """,
                (rs, rowNum) -> new UserAdminDto(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getObject("department_id") == null ? null : rs.getLong("department_id"),
                        rs.getString("department_name"),
                        rs.getString("status"),
                        findRoles(rs.getLong("id"))));
    }

    @Override
    @Transactional
    public UserAdminDto create(String username, String displayName, String passwordHash, Long departmentId, List<String> roles) {
        Long id = jdbcTemplate.queryForObject("""
                        insert into app_user (username, display_name, password_hash, department_id, status)
                        values (?, ?, ?, ?, 'ACTIVE')
                        returning id
                        """,
                Long.class,
                username,
                displayName,
                passwordHash,
                departmentId);
        if (id == null) {
            throw new UserAdminException("USER_CREATE_FAILED", "User was not created");
        }
        replaceRoles(id, roles);
        return findById(id).orElseThrow();
    }

    @Override
    @Transactional
    public Optional<UserAdminDto> update(long id, String displayName, Long departmentId, String status, List<String> roles) {
        int updated = jdbcTemplate.update("""
                update app_user
                set display_name = ?, department_id = ?, status = ?, updated_at = now()
                where id = ?
                """, displayName, departmentId, status, id);
        if (updated == 0) {
            return Optional.empty();
        }
        replaceRoles(id, roles);
        return findById(id);
    }

    @Override
    public boolean resetPassword(long id, String passwordHash) {
        return jdbcTemplate.update("update app_user set password_hash = ?, updated_at = now() where id = ?", passwordHash, id) > 0;
    }

    @Override
    public boolean disable(long id) {
        return jdbcTemplate.update("update app_user set status = 'DISABLED', updated_at = now() where id = ?", id) > 0;
    }

    private Optional<UserAdminDto> findById(long id) {
        return jdbcTemplate.query("""
                        select u.id, u.username, u.display_name, u.department_id, d.name as department_name, u.status
                        from app_user u
                        left join department d on d.id = u.department_id
                        where u.id = ?
                        """,
                (rs, rowNum) -> new UserAdminDto(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getObject("department_id") == null ? null : rs.getLong("department_id"),
                        rs.getString("department_name"),
                        rs.getString("status"),
                        findRoles(rs.getLong("id"))),
                id)
                .stream()
                .findFirst();
    }

    private List<String> findRoles(long userId) {
        return jdbcTemplate.query("""
                        select role_code
                        from app_user_role
                        where user_id = ?
                        order by role_code
                        """,
                (rs, rowNum) -> rs.getString("role_code"),
                userId);
    }

    private void replaceRoles(long userId, List<String> roles) {
        jdbcTemplate.update("delete from app_user_role where user_id = ?", userId);
        for (String role : roles) {
            jdbcTemplate.update("insert into app_user_role (user_id, role_code) values (?, ?)", userId, role);
        }
    }
}
