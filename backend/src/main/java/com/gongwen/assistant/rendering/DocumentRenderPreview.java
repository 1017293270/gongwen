package com.gongwen.assistant.rendering;

import java.time.Instant;

public record DocumentRenderPreview(
        Long id,
        Long draftId,
        long templateVersionId,
        String sourceFileHash,
        String renderer,
        String rendererVersion,
        DocumentRenderPreviewStatus status,
        int pageCount,
        String storagePath,
        DocumentRenderPreviewManifest manifest,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public DocumentRenderPreview {
        status = status == null ? DocumentRenderPreviewStatus.PENDING : status;
        pageCount = Math.max(pageCount, 0);
        manifest = manifest == null ? DocumentRenderPreviewManifest.empty() : manifest;
    }

    public static DocumentRenderPreview pending(long templateVersionId, String sourceFileHash, String renderer) {
        Instant now = Instant.now();
        return new DocumentRenderPreview(
                null,
                null,
                templateVersionId,
                sourceFileHash,
                renderer,
                null,
                DocumentRenderPreviewStatus.PENDING,
                0,
                null,
                DocumentRenderPreviewManifest.empty(),
                null,
                null,
                now,
                now
        );
    }
}
