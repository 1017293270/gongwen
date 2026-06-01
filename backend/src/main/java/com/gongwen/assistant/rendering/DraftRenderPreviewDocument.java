package com.gongwen.assistant.rendering;

public record DraftRenderPreviewDocument(
        long templateVersionId,
        String sourceFileHash,
        String fileName,
        byte[] content
) {
    public DraftRenderPreviewDocument {
        if (content == null || content.length == 0) {
            throw new RenderPreviewException("DRAFT_RENDER_PREVIEW_EMPTY", "Draft render preview document is empty");
        }
    }
}
