package com.gongwen.assistant.organization;

import java.util.List;

public record UserAdminDto(
        long id,
        String username,
        String displayName,
        Long departmentId,
        String departmentName,
        String status,
        List<String> roles
) {
}
