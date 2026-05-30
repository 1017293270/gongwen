package com.gongwen.assistant.documentstructure.mapping;

public record StructureMappingValidationItem(
        String severity,
        String code,
        String message,
        String nodeKey,
        String role
) {
    public StructureMappingValidationItem {
        severity = severity == null || severity.isBlank() ? "BLOCKING" : severity;
        code = code == null ? "" : code;
        message = message == null ? "" : message;
    }
}
