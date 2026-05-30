package com.gongwen.assistant.security;

import java.util.List;

public record CurrentUser(
        long id,
        String username,
        String displayName,
        Long departmentId,
        String departmentName,
        List<String> roles
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean systemAdmin() {
        return hasRole("SYSTEM_ADMIN");
    }

    public boolean templateAdmin() {
        return hasRole("TEMPLATE_ADMIN") || systemAdmin();
    }
}
