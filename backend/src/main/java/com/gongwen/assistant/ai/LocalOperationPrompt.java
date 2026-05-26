package com.gongwen.assistant.ai;

import java.util.List;

public record LocalOperationPrompt(
        String promptVersion,
        String documentTypeCode,
        String title,
        long targetBlockId,
        int targetSortOrder,
        AiLocalOperationType operationType,
        String originalText,
        List<String> fieldSummaries,
        List<String> materialSummaries,
        String instruction,
        String inputSummary
) {
}
