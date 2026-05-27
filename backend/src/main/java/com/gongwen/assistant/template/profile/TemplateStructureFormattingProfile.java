package com.gongwen.assistant.template.profile;

public record TemplateStructureFormattingProfile(
        String fontFamily,
        Integer fontSizeHalfPoints,
        Boolean bold,
        String alignment,
        Integer indentationFirstLine,
        Integer spacingBetween,
        Integer spacingBefore,
        Integer spacingAfter
) {
}
