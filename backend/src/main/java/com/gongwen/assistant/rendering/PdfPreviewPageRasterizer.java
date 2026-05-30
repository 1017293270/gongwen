package com.gongwen.assistant.rendering;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfPreviewPageRasterizer {
    private final RenderPreviewProperties properties;

    public PdfPreviewPageRasterizer(RenderPreviewProperties properties) {
        this.properties = properties;
    }

    public List<DocumentRenderPreviewPage> rasterize(Path pdfPath, Path outputDir) {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<DocumentRenderPreviewPage> pages = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, properties.dpi(), ImageType.RGB);
                String fileName = "page-%03d.png".formatted(pageIndex + 1);
                Path outputFile = outputDir.resolve(fileName);
                ImageIO.write(image, "png", outputFile.toFile());
                pages.add(new DocumentRenderPreviewPage(
                        pageIndex + 1,
                        fileName,
                        "image/png",
                        image.getWidth(),
                        image.getHeight(),
                        properties.dpi()
                ));
            }
            return pages;
        } catch (IOException exception) {
            throw new RenderPreviewException("RENDER_PREVIEW_RASTERIZE_FAILED", "PDF preview rasterization failed", exception);
        }
    }
}
