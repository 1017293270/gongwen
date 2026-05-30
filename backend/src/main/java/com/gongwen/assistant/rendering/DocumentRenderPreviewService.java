package com.gongwen.assistant.rendering;

import com.gongwen.assistant.template.TemplateVersion;
import com.gongwen.assistant.template.TemplateVersionRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DocumentRenderPreviewService {
    private final DocumentRenderPreviewRepository repository;
    private final TemplateVersionRepository templateVersionRepository;
    private final DocumentRenderPreviewRenderer renderer;
    private final RenderPreviewProperties properties;
    private final RenderPreviewJobExecutor jobExecutor;

    public DocumentRenderPreviewService(
            DocumentRenderPreviewRepository repository,
            TemplateVersionRepository templateVersionRepository,
            DocumentRenderPreviewRenderer renderer,
            RenderPreviewProperties properties,
            RenderPreviewJobExecutor jobExecutor
    ) {
        this.repository = repository;
        this.templateVersionRepository = templateVersionRepository;
        this.renderer = renderer;
        this.properties = properties;
        this.jobExecutor = jobExecutor;
    }

    public DocumentRenderPreview getStatus(long templateVersionId) {
        TemplateVersion version = findTemplateVersion(templateVersionId);
        return repository.findLatestByTemplateVersionId(templateVersionId)
                .orElseGet(() -> DocumentRenderPreview.pending(
                        templateVersionId,
                        version.profileHash(),
                        renderer.rendererName()
                ));
    }

    public DocumentRenderPreview requestRender(long templateVersionId) {
        TemplateVersion version = findTemplateVersion(templateVersionId);
        DocumentRenderPreview job = repository.create(new DocumentRenderPreview(
                null,
                templateVersionId,
                version.profileHash(),
                renderer.rendererName(),
                null,
                DocumentRenderPreviewStatus.RENDERING,
                0,
                null,
                DocumentRenderPreviewManifest.empty(),
                null,
                null,
                null,
                null
        ));
        jobExecutor.submit(() -> runRenderJob(job, version));
        return repository.findById(job.id()).orElse(job);
    }

    public DocumentRenderPreviewFile getPage(long previewId, int pageNumber) {
        DocumentRenderPreview preview = repository.findById(previewId)
                .orElseThrow(() -> new RenderPreviewException("RENDER_PREVIEW_NOT_FOUND", "Render preview not found"));
        if (preview.status() != DocumentRenderPreviewStatus.READY) {
            throw new RenderPreviewException("RENDER_PREVIEW_NOT_READY", "Render preview is not ready");
        }
        DocumentRenderPreviewPage page = preview.manifest().pages().stream()
                .filter(candidate -> candidate.pageNumber() == pageNumber)
                .findFirst()
                .orElseThrow(() -> new RenderPreviewException("RENDER_PREVIEW_PAGE_NOT_FOUND", "Render preview page not found"));
        Path previewDir = requireInsideStorageRoot(Path.of(preview.storagePath()).toAbsolutePath().normalize());
        Path pagePath = previewDir.resolve(page.fileName()).normalize();
        requireInside(previewDir, pagePath);
        requireInside(storageRoot(), pagePath);
        try {
            return new DocumentRenderPreviewFile(page.fileName(), page.contentType(), Files.readAllBytes(pagePath));
        } catch (IOException exception) {
            throw new RenderPreviewException("RENDER_PREVIEW_PAGE_UNAVAILABLE", "Render preview page file is unavailable", exception);
        }
    }

    private void runRenderJob(DocumentRenderPreview job, TemplateVersion version) {
        Path outputDir = null;
        try {
            Path sourceDocx = Path.of(version.filePath()).toAbsolutePath().normalize();
            if (!Files.exists(sourceDocx)) {
                throw new RenderPreviewException("RENDER_PREVIEW_SOURCE_MISSING", "Template source file is missing");
            }
            Path root = storageRoot();
            outputDir = root.resolve("template-version-" + version.id())
                    .resolve("preview-" + job.id())
                    .normalize();
            requireInside(root, outputDir);
            Files.createDirectories(outputDir);

            RenderedDocumentPreview rendered = renderer.render(sourceDocx, outputDir);
            DocumentRenderPreviewManifest manifest = new DocumentRenderPreviewManifest(
                    1,
                    rendered.pdfFileName(),
                    rendered.pages()
            );
            repository.updateResult(
                    job.id(),
                    DocumentRenderPreviewStatus.READY,
                    manifest.pages().size(),
                    outputDir.toString(),
                    manifest,
                    rendered.rendererVersion(),
                    null,
                    null
            );
        } catch (RenderPreviewException exception) {
            markFailed(job.id(), outputDir, exception);
        } catch (IOException exception) {
            markFailed(job.id(), outputDir, new RenderPreviewException(
                    "RENDER_PREVIEW_STORAGE_FAILED",
                    "Render preview storage failed",
                    exception
            ));
        } catch (RuntimeException exception) {
            markFailed(job.id(), outputDir, new RenderPreviewException(
                    "RENDER_PREVIEW_FAILED",
                    "Render preview failed",
                    exception
            ));
        }
    }

    private void markFailed(long jobId, Path outputDir, RenderPreviewException exception) {
        repository.updateResult(
                jobId,
                exception.failureStatus(),
                0,
                outputDir == null ? null : outputDir.toString(),
                DocumentRenderPreviewManifest.empty(),
                null,
                exception.errorCode(),
                exception.getMessage()
        );
    }

    private TemplateVersion findTemplateVersion(long templateVersionId) {
        return templateVersionRepository.findById(templateVersionId)
                .orElseThrow(() -> new RenderPreviewException("TEMPLATE_VERSION_NOT_FOUND", "Template version not found"));
    }

    private Path storageRoot() {
        return Path.of(properties.storageDir()).toAbsolutePath().normalize();
    }

    private Path requireInsideStorageRoot(Path path) {
        return requireInside(storageRoot(), path);
    }

    private Path requireInside(Path root, Path path) {
        if (!path.startsWith(root)) {
            throw new RenderPreviewException("RENDER_PREVIEW_PATH_INVALID", "Render preview path is outside storage directory");
        }
        return path;
    }
}
