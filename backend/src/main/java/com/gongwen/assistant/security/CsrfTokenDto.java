package com.gongwen.assistant.security;

public record CsrfTokenDto(
        String headerName,
        String parameterName,
        String token
) {
}
