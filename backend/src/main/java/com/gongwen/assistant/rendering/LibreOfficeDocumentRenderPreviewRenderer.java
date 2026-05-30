package com.gongwen.assistant.rendering;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class LibreOfficeDocumentRenderPreviewRenderer implements DocumentRenderPreviewRenderer {
    private final LibreOfficeRenderClient libreOfficeRenderClient;
    private final PdfPreviewPageRasterizer pageRasterizer;

    public LibreOfficeDocumentRenderPreviewRenderer(
            LibreOfficeRenderClient libreOfficeRenderClient,
            PdfPreviewPageRasterizer pageRasterizer
    ) {
        this.libreOfficeRenderClient = libreOfficeRenderClient;
        this.pageRasterizer = pageRasterizer;
    }

    @Override
    public RenderedDocumentPreview render(Path sourceDocx, Path outputDir) {
        Path pdfPath = libreOfficeRenderClient.renderToPdf(sourceDocx, outputDir);
        List<DocumentRenderPreviewPage> pages = pageRasterizer.rasterize(pdfPath, outputDir);
        return new RenderedDocumentPreview(rendererName(), null, pdfPath.getFileName().toString(), pages);
    }

    @Override
    public String rendererName() {
        return "libreoffice";
    }
}
