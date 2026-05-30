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
    public static DraftNodeFormatOverride empty() {
        return new DraftNodeFormatOverride(null, null, null, null, null, null, null, null, null, null);
    }
}
