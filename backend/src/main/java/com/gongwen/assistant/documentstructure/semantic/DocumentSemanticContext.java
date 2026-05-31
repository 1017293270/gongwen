package com.gongwen.assistant.documentstructure.semantic;

public record DocumentSemanticContext(
        String documentKind,
        String documentTypeCode
) {
    public DocumentSemanticContext {
        documentKind = documentKind == null || documentKind.isBlank() ? "UNKNOWN_DOCUMENT" : documentKind;
        documentTypeCode = documentTypeCode == null || documentTypeCode.isBlank() ? "UNKNOWN" : documentTypeCode;
    }
}
