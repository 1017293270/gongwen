package com.gongwen.assistant.material;

public record MaterialDto(
        long id,
        long draftId,
        String originalFileName,
        String contentType,
        long fileSizeBytes,
        String fileExtension,
        String status,
        int extractedTextLength,
        String errorMessage
) {
}
