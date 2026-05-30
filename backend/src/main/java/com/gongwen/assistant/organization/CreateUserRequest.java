package com.gongwen.assistant.organization;

import java.util.List;

public record CreateUserRequest(
        String username,
        String displayName,
        String password,
        Long departmentId,
        List<String> roles
) {
}
