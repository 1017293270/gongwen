package com.gongwen.assistant.organization;

import java.util.List;

public record UserAccountRecord(
        long id,
        String username,
        String displayName,
        String passwordHash,
        Long departmentId,
        String departmentName,
        String status,
        List<String> roles
) {
}
