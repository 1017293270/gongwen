package com.gongwen.assistant.rendering;

import java.util.List;

public record DocumentRenderPreviewManifest(
        int schemaVersion,
        String pdfFileName,
        List<DocumentRenderPreviewPage> pages
) {
    public DocumentRenderPreviewManifest {
        if (schemaVersion <= 0) {
            schemaVersion = 1;
        }
        pages = pages == null ? List.of() : List.copyOf(pages);
    }

    public static DocumentRenderPreviewManifest empty() {
        return new DocumentRenderPreviewManifest(1, null, List.of());
    }
}
