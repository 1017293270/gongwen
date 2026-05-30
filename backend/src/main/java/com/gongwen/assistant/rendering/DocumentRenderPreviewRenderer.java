package com.gongwen.assistant.rendering;

import java.nio.file.Path;

public interface DocumentRenderPreviewRenderer {
    RenderedDocumentPreview render(Path sourceDocx, Path outputDir);

    String rendererName();
}
