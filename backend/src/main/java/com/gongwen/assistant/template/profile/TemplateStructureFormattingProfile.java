package com.gongwen.assistant.template.profile;

public record TemplateStructureFormattingProfile(
        String fontFamily,
        Integer fontSizeHalfPoints,
        Boolean bold,
        String alignment,
        Integer indentationFirstLine,
        Integer spacingBetween,
        Integer spacingBefore,
        Integer spacingAfter,
        String colorHex
) {
    public TemplateStructureFormattingProfile(
            String fontFamily,
            Integer fontSizeHalfPoints,
            Boolean bold,
            String alignment,
            Integer indentationFirstLine,
            Integer spacingBetween,
            Integer spacingBefore,
            Integer spacingAfter
    ) {
        this(fontFamily, fontSizeHalfPoints, bold, alignment, indentationFirstLine, spacingBetween, spacingBefore, spacingAfter, null);
    }

    public TemplateStructureFormattingProfile mergeOverride(TemplateStructureFormattingProfile override) {
        if (override == null) {
            return this;
        }
        return new TemplateStructureFormattingProfile(
                preferNonBlank(override.fontFamily(), fontFamily),
                override.fontSizeHalfPoints() != null ? override.fontSizeHalfPoints() : fontSizeHalfPoints,
                override.bold() != null ? override.bold() : bold,
                preferNonBlank(override.alignment(), alignment),
                override.indentationFirstLine() != null ? override.indentationFirstLine() : indentationFirstLine,
                override.spacingBetween() != null ? override.spacingBetween() : spacingBetween,
                override.spacingBefore() != null ? override.spacingBefore() : spacingBefore,
                override.spacingAfter() != null ? override.spacingAfter() : spacingAfter,
                preferNonBlank(override.colorHex(), colorHex)
        );
    }

    private static String preferNonBlank(String overrideValue, String fallbackValue) {
        if (overrideValue == null || overrideValue.isBlank()) {
            return fallbackValue;
        }
        return overrideValue;
    }
}
