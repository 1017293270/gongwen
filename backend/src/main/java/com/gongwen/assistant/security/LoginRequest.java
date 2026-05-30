package com.gongwen.assistant.security;

public record LoginRequest(
        String username,
        String password
) {
}
