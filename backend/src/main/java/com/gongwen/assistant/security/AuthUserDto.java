package com.gongwen.assistant.security;

import java.util.List;

public record AuthUserDto(
        long id,
        String username,
        String displayName,
        Long departmentId,
        String departmentName,
        List<String> roles
) {
}
