package com.gongwen.assistant.document;

public record CreateDocumentTypeRequest(
        String code,
        String name,
        Integer sortOrder
) {
}
