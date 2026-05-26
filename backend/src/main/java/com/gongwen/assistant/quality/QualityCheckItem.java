package com.gongwen.assistant.quality;

public record QualityCheckItem(
        String severity,
        String category,
        String code,
        String message,
        String targetBlockType,
        Long targetBlockId,
        String suggestion
) {
}
