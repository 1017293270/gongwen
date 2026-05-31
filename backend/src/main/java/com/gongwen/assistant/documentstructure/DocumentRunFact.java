package com.gongwen.assistant.documentstructure;

public record DocumentRunFact(
        int runIndex,
        String text,
        String eastAsiaFontFamily,
        String latinFontFamily,
        Integer fontSizeHalfPoints,
        Boolean bold,
        Boolean italic,
        String colorHex
) {
    public DocumentRunFact {
        text = text == null ? "" : text;
    }
}
