package com.gongwen.assistant.organization;

import java.util.List;
import java.util.Optional;

public interface UserAdminRepository {
    List<UserAdminDto> findAll();

    UserAdminDto create(String username, String displayName, String passwordHash, Long departmentId, List<String> roles);

    Optional<UserAdminDto> update(long id, String displayName, Long departmentId, String status, List<String> roles);

    boolean resetPassword(long id, String passwordHash);

    boolean disable(long id);
}
