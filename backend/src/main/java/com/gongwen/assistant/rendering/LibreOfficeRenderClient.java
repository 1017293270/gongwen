package com.gongwen.assistant.rendering;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
public class LibreOfficeRenderClient {
    private static final int MAX_ERROR_CHARS = 1200;

    private final RenderPreviewProperties properties;

    public LibreOfficeRenderClient(RenderPreviewProperties properties) {
        this.properties = properties;
    }

    public Path renderToPdf(Path inputDocx, Path outputDir) {
        if (!"libreoffice".equalsIgnoreCase(properties.renderer())) {
            throw new RenderPreviewException(
                    "RENDER_PREVIEW_UNSUPPORTED",
                    "Render preview renderer is not supported: " + properties.renderer(),
                    DocumentRenderPreviewStatus.UNSUPPORTED
            );
        }
        try {
            Files.createDirectories(outputDir);
            Path logFile = outputDir.resolve("libreoffice-render.log");
            Process process = new ProcessBuilder(buildCommand(inputDocx, outputDir))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
                    .start();
            boolean finished = process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS);
            String output = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
            if (!finished) {
                process.destroyForcibly();
                throw new RenderPreviewException("RENDER_PREVIEW_TIMEOUT", "LibreOffice render preview timed out");
            }
            if (process.exitValue() != 0) {
                throw new RenderPreviewException(
                        "RENDER_PREVIEW_FAILED",
                        "LibreOffice render preview failed: " + truncate(output)
                );
            }
            return findConvertedPdf(inputDocx, outputDir);
        } catch (IOException exception) {
            throw new RenderPreviewException(
                    "RENDER_PREVIEW_UNSUPPORTED",
                    "LibreOffice is unavailable for render preview",
                    DocumentRenderPreviewStatus.UNSUPPORTED,
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RenderPreviewException("RENDER_PREVIEW_INTERRUPTED", "Render preview was interrupted", exception);
        }
    }

    public List<String> buildCommand(Path inputDocx, Path outputDir) {
        return List.of(
                properties.libreOfficePath(),
                "--headless",
                "--convert-to",
                "pdf",
                "--outdir",
                outputDir.toString(),
                inputDocx.toString()
        );
    }

    private Path findConvertedPdf(Path inputDocx, Path outputDir) throws IOException {
        String expectedName = stripExtension(inputDocx.getFileName().toString()) + ".pdf";
        Path expected = outputDir.resolve(expectedName);
        if (Files.exists(expected)) {
            return expected;
        }
        try (Stream<Path> files = Files.list(outputDir)) {
            return files
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .min(Comparator.comparing(path -> path.getFileName().toString()))
                    .orElseThrow(() -> new RenderPreviewException(
                            "RENDER_PREVIEW_PDF_MISSING",
                            "LibreOffice did not produce a PDF preview"
                    ));
        }
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_CHARS) {
            return value;
        }
        return value.substring(0, MAX_ERROR_CHARS);
    }
}
