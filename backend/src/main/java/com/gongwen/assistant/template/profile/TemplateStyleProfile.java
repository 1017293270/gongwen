package com.gongwen.assistant.template.profile;

public record TemplateStyleProfile(
        String styleId,
        String styleName,
        String type,
        String basedOn,
        String fontFamily,
        String eastAsiaFontFamily,
        String latinFontFamily,
        Integer fontSizeHalfPoints,
        Boolean bold,
        String alignment,
        Integer indentationFirstLine,
        Integer spacingBetween,
        Integer spacingBefore,
        Integer spacingAfter,
        TemplateLineSpacingProfile lineSpacing
) {
}
