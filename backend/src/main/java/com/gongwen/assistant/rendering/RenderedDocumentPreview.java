package com.gongwen.assistant.rendering;

import java.util.List;

public record RenderedDocumentPreview(
        String renderer,
        String rendererVersion,
        String pdfFileName,
        List<DocumentRenderPreviewPage> pages
) {
    public RenderedDocumentPreview {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }
}
