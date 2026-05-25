package com.gongwen.assistant.document;

public record DocumentTypeDto(
        String code,
        String name,
        String status,
        int sortOrder
) {
}
