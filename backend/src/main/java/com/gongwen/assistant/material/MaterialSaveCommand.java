package com.gongwen.assistant.material;

public record MaterialSaveCommand(
        long draftId,
        String originalFileName,
        String contentType,
        long fileSizeBytes,
        String fileExtension,
        String storagePath,
        String status,
        String extractedText,
        String errorMessage
) {
    MaterialDto toDto(long id) {
        return new MaterialDto(
                id,
                draftId,
                originalFileName,
                contentType,
                fileSizeBytes,
                fileExtension,
                status,
                extractedText == null ? 0 : extractedText.length(),
                errorMessage
        );
    }
}
