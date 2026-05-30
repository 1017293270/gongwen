package com.gongwen.assistant.draft.node;

public record DraftNodeFormatOverride(
        String eastAsiaFont,
        String latinFont,
        Double fontSizePt,
        Boolean bold,
        String alignment,
        Integer firstLineIndentTwip,
        String lineSpacingRule,
        Integer lineSpacingTwip,
        Integer spacingBeforeTwip,
        Integer spacingAfterTwip
) {
    public DraftNodeFormatOverride {
        eastAsiaFont = normalizeBlank(eastAsiaFont);
        latinFont = normalizeBlank(latinFont);
        alignment = normalizeUpper(alignment);
        lineSpacingRule = normalizeUpper(lineSpacingRule);
    }

    public static DraftNodeFormatOverride empty() {
        return new DraftNodeFormatOverride(null, null, null, null, null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return eastAsiaFont == null
                && latinFont == null
                && fontSizePt == null
                && bold == null
                && alignment == null
                && firstLineIndentTwip == null
                && lineSpacingRule == null
                && lineSpacingTwip == null
                && spacingBeforeTwip == null
                && spacingAfterTwip == null;
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static String normalizeUpper(String value) {
        String normalized = normalizeBlank(value);
        return normalized == null ? null : normalized.toUpperCase();
    }
}
