package com.gongwen.assistant.organization;

import java.util.List;

public record UpdateUserRequest(
        String displayName,
        Long departmentId,
        String status,
        List<String> roles
) {
}
