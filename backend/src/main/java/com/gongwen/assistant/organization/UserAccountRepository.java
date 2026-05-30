package com.gongwen.assistant.organization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class UserAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccountRecord> findActiveByUsername(String username) {
        return findByUsernameAndStatus(username, "ACTIVE");
    }

    public Optional<UserAccountRecord> findByUsernameAndStatus(String username, String status) {
        String normalizedUsername = username == null ? "" : username.strip();
        List<UserAccountRecord> users = jdbcTemplate.query("""
                        select u.id,
                               u.username,
                               u.display_name,
                               u.password_hash,
                               u.department_id,
                               d.name as department_name,
                               u.status
                        from app_user u
                        left join department d on d.id = u.department_id
                        where u.username = ? and u.status = ?
                        """,
                (rs, rowNum) -> new UserAccountRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("password_hash"),
                        rs.getObject("department_id") == null ? null : rs.getLong("department_id"),
                        rs.getString("department_name"),
                        rs.getString("status"),
                        findRoles(rs.getLong("id"))),
                normalizedUsername,
                status);
        return users.stream().findFirst();
    }

    public int countUsers() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from app_user", Integer.class);
        return count == null ? 0 : count;
    }

    public long rootDepartmentId() {
        Long id = jdbcTemplate.queryForObject("select id from department where code = 'ROOT'", Long.class);
        if (id == null) {
            throw new IllegalStateException("Root department is missing");
        }
        return id;
    }

    @Transactional
    public long createUser(String username, String displayName, String passwordHash, Long departmentId, List<String> roles) {
        Long userId = jdbcTemplate.queryForObject("""
                        insert into app_user (username, display_name, password_hash, department_id, status)
                        values (?, ?, ?, ?, 'ACTIVE')
                        returning id
                        """,
                Long.class,
                username,
                displayName,
                passwordHash,
                departmentId);
        if (userId == null) {
            throw new IllegalStateException("User was not created");
        }
        replaceRoles(userId, roles);
        return userId;
    }

    public void recordLogin(long userId) {
        jdbcTemplate.update("update app_user set last_login_at = now(), updated_at = now() where id = ?", userId);
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
            jdbcTemplate.update("""
                    insert into app_user_role (user_id, role_code)
                    values (?, ?)
                    """, userId, role);
        }
    }
}
