package com.gongwen.assistant.document;

public record UpdateDocumentTypeRequest(
        String name,
        Integer sortOrder
) {
}
