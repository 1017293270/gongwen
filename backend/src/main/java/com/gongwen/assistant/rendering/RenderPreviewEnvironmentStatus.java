package com.gongwen.assistant.rendering;

public record RenderPreviewEnvironmentStatus(
        String renderer,
        boolean available,
        String libreOfficePath,
        String message
) {
}
