package com.gongwen.assistant.rendering;

public record DocumentRenderPreviewFile(
        String fileName,
        String contentType,
        byte[] content
) {
}
