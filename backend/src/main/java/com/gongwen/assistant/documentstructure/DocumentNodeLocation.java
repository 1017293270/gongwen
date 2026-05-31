package com.gongwen.assistant.documentstructure;

public record DocumentNodeLocation(
        String part,
        Integer paragraphIndex,
        Integer tableIndex,
        Integer rowIndex,
        Integer cellIndex,
        Integer cellParagraphIndex
) {
    public DocumentNodeLocation {
        part = part == null || part.isBlank() ? "BODY" : part;
    }
}
