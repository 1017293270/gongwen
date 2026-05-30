package com.gongwen.assistant.rendering;

import java.util.Optional;

public interface DocumentRenderPreviewRepository {
    DocumentRenderPreview create(DocumentRenderPreview preview);

    void updateResult(
            long id,
            DocumentRenderPreviewStatus status,
            int pageCount,
            String storagePath,
            DocumentRenderPreviewManifest manifest,
            String rendererVersion,
            String errorCode,
            String errorMessage
    );

    Optional<DocumentRenderPreview> findLatestByTemplateVersionId(long templateVersionId);

    Optional<DocumentRenderPreview> findById(long id);
}
