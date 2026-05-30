package com.gongwen.assistant.rendering;

public record DocumentRenderPreviewPage(
        int pageNumber,
        String fileName,
        String contentType,
        int widthPixels,
        int heightPixels,
        int dpi
) {
    public DocumentRenderPreviewPage {
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/png";
        }
    }
}
