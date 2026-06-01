package com.gongwen.assistant.exporting;

public record DraftWordRenderResult(
        long templateVersionId,
        String fileName,
        byte[] content
) {
}
