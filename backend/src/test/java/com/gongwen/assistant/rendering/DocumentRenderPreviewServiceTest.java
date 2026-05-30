package com.gongwen.assistant.rendering;

import com.gongwen.assistant.template.TemplateVersion;
import com.gongwen.assistant.template.TemplateVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentRenderPreviewServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void statusIsPendingBeforeRenderJobExists() throws IOException {
        Path sourceDocx = Files.writeString(tempDir.resolve("notice.docx"), "docx");
        InMemoryTemplateVersionRepository versions = new InMemoryTemplateVersionRepository();
        versions.save(version(7L, sourceDocx));
        DocumentRenderPreviewService service = service(
                versions,
                new InMemoryDocumentRenderPreviewRepository(),
                new FakeRenderer()
        );

        DocumentRenderPreview status = service.getStatus(7L);

        assertThat(status.id()).isNull();
        assertThat(status.status()).isEqualTo(DocumentRenderPreviewStatus.PENDING);
        assertThat(status.sourceFileHash()).isEqualTo("hash-7");
    }

    @Test
    void renderJobStoresReadyPreviewAndServesPageInsideStorageRoot() throws IOException {
        Path sourceDocx = Files.writeString(tempDir.resolve("notice.docx"), "docx");
        InMemoryTemplateVersionRepository versions = new InMemoryTemplateVersionRepository();
        versions.save(version(7L, sourceDocx));
        InMemoryDocumentRenderPreviewRepository previews = new InMemoryDocumentRenderPreviewRepository();
        DocumentRenderPreviewService service = service(versions, previews, new FakeRenderer());

        DocumentRenderPreview preview = service.requestRender(7L);

        assertThat(preview.status()).isEqualTo(DocumentRenderPreviewStatus.READY);
        assertThat(preview.pageCount()).isEqualTo(1);
        assertThat(Path.of(preview.storagePath())).startsWith(previewRoot());
        assertThat(preview.manifest().pages()).extracting(DocumentRenderPreviewPage::pageNumber).containsExactly(1);

        DocumentRenderPreviewFile file = service.getPage(preview.id(), 1);
        assertThat(file.fileName()).isEqualTo("page-001.png");
        assertThat(file.contentType()).isEqualTo("image/png");
        assertThat(file.content()).isEqualTo("png".getBytes());
    }

    @Test
    void renderJobStoresUnsupportedStatusWhenRendererCannotRun() throws IOException {
        Path sourceDocx = Files.writeString(tempDir.resolve("notice.docx"), "docx");
        InMemoryTemplateVersionRepository versions = new InMemoryTemplateVersionRepository();
        versions.save(version(8L, sourceDocx));
        DocumentRenderPreviewService service = service(versions, new InMemoryDocumentRenderPreviewRepository(),
                new UnsupportedFakeRenderer());

        DocumentRenderPreview preview = service.requestRender(8L);

        assertThat(preview.status()).isEqualTo(DocumentRenderPreviewStatus.UNSUPPORTED);
        assertThat(preview.errorCode()).isEqualTo("RENDER_PREVIEW_UNSUPPORTED");
        assertThat(preview.errorMessage()).isEqualTo("renderer unavailable");
    }

    @Test
    void pageDownloadRejectsManifestPathTraversal() throws IOException {
        Path sourceDocx = Files.writeString(tempDir.resolve("notice.docx"), "docx");
        InMemoryTemplateVersionRepository versions = new InMemoryTemplateVersionRepository();
        versions.save(version(9L, sourceDocx));
        DocumentRenderPreviewService service = service(versions, new InMemoryDocumentRenderPreviewRepository(),
                new PathTraversalFakeRenderer());

        DocumentRenderPreview preview = service.requestRender(9L);

        assertThatThrownBy(() -> service.getPage(preview.id(), 1))
                .isInstanceOfSatisfying(RenderPreviewException.class, exception ->
                        assertThat(((RenderPreviewException) exception).errorCode()).isEqualTo("RENDER_PREVIEW_PATH_INVALID"));
    }

    private DocumentRenderPreviewService service(
            InMemoryTemplateVersionRepository versions,
            InMemoryDocumentRenderPreviewRepository previews,
            DocumentRenderPreviewRenderer renderer
    ) {
        return new DocumentRenderPreviewService(
                previews,
                versions,
                renderer,
                new RenderPreviewProperties(previewRoot().toString(), renderer.rendererName(), "soffice", 150, 5),
                Runnable::run
        );
    }

    private Path previewRoot() {
        return tempDir.resolve("previews").toAbsolutePath().normalize();
    }

    private TemplateVersion version(long id, Path sourceDocx) {
        return new TemplateVersion(
                id,
                3L,
                1,
                sourceDocx.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                4,
                sourceDocx.toString(),
                "hash-" + id,
                "READY",
                null,
                null,
                Instant.now()
        );
    }

    private static class FakeRenderer implements DocumentRenderPreviewRenderer {
        @Override
        public RenderedDocumentPreview render(Path sourceDocx, Path outputDir) {
            try {
                Files.write(outputDir.resolve("page-001.png"), "png".getBytes());
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            return new RenderedDocumentPreview(
                    rendererName(),
                    "test-renderer",
                    "notice.pdf",
                    List.of(new DocumentRenderPreviewPage(1, "page-001.png", "image/png", 100, 200, 150))
            );
        }

        @Override
        public String rendererName() {
            return "fake";
        }
    }

    private static class UnsupportedFakeRenderer implements DocumentRenderPreviewRenderer {
        @Override
        public RenderedDocumentPreview render(Path sourceDocx, Path outputDir) {
            throw new RenderPreviewException(
                    "RENDER_PREVIEW_UNSUPPORTED",
                    "renderer unavailable",
                    DocumentRenderPreviewStatus.UNSUPPORTED
            );
        }

        @Override
        public String rendererName() {
            return "fake";
        }
    }

    private static class PathTraversalFakeRenderer implements DocumentRenderPreviewRenderer {
        @Override
        public RenderedDocumentPreview render(Path sourceDocx, Path outputDir) {
            return new RenderedDocumentPreview(
                    rendererName(),
                    "test",
                    null,
                    List.of(new DocumentRenderPreviewPage(1, "../escape.png", "image/png", 10, 10, 150))
            );
        }

        @Override
        public String rendererName() {
            return "fake";
        }
    }

    private static class InMemoryDocumentRenderPreviewRepository implements DocumentRenderPreviewRepository {
        private final Map<Long, DocumentRenderPreview> previews = new HashMap<>();
        private long nextId = 1L;

        @Override
        public DocumentRenderPreview create(DocumentRenderPreview preview) {
            long id = nextId++;
            DocumentRenderPreview saved = copy(preview, id, preview.status(), preview.pageCount(), preview.storagePath(),
                    preview.manifest(), preview.rendererVersion(), preview.errorCode(), preview.errorMessage());
            previews.put(id, saved);
            return saved;
        }

        @Override
        public void updateResult(
                long id,
                DocumentRenderPreviewStatus status,
                int pageCount,
                String storagePath,
                DocumentRenderPreviewManifest manifest,
                String rendererVersion,
                String errorCode,
                String errorMessage
        ) {
            DocumentRenderPreview current = previews.get(id);
            previews.put(id, copy(current, id, status, pageCount, storagePath, manifest, rendererVersion, errorCode, errorMessage));
        }

        @Override
        public Optional<DocumentRenderPreview> findLatestByTemplateVersionId(long templateVersionId) {
            return previews.values().stream()
                    .filter(preview -> preview.templateVersionId() == templateVersionId)
                    .max(Comparator.comparing(DocumentRenderPreview::createdAt));
        }

        @Override
        public Optional<DocumentRenderPreview> findById(long id) {
            return Optional.ofNullable(previews.get(id));
        }

        private DocumentRenderPreview copy(
                DocumentRenderPreview preview,
                long id,
                DocumentRenderPreviewStatus status,
                int pageCount,
                String storagePath,
                DocumentRenderPreviewManifest manifest,
                String rendererVersion,
                String errorCode,
                String errorMessage
        ) {
            Instant now = Instant.now();
            return new DocumentRenderPreview(
                    id,
                    preview.templateVersionId(),
                    preview.sourceFileHash(),
                    preview.renderer(),
                    rendererVersion,
                    status,
                    pageCount,
                    storagePath,
                    manifest,
                    errorCode,
                    errorMessage,
                    preview.createdAt() == null ? now : preview.createdAt(),
                    now
            );
        }
    }

    private static class InMemoryTemplateVersionRepository implements TemplateVersionRepository {
        private final Map<Long, TemplateVersion> versions = new HashMap<>();

        void save(TemplateVersion version) {
            versions.put(version.id(), version);
        }

        @Override
        public TemplateVersion create(long templateId, String originalFileName, String contentType, long fileSizeBytes, String filePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextVersionNo(long templateId) {
            return 1;
        }

        @Override
        public Optional<TemplateVersion> findById(long id) {
            return Optional.ofNullable(versions.get(id));
        }

        @Override
        public void markParsed(long id, String profileHash) {
        }

        @Override
        public void markFailed(long id, String errorCode, String errorMessage) {
        }
    }
}
