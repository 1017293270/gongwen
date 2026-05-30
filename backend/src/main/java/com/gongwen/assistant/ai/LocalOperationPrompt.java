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
        String inputSummary,
        Long targetNodeId,
        String targetNodeRole,
        String targetNodeTitle,
        String nodeContext
) {
    public LocalOperationPrompt(
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
        this(promptVersion, documentTypeCode, title, targetBlockId, targetSortOrder, operationType, originalText,
                fieldSummaries, materialSummaries, instruction, inputSummary, null, "", "", "");
    }

    public LocalOperationPrompt {
        fieldSummaries = List.copyOf(fieldSummaries);
        materialSummaries = List.copyOf(materialSummaries);
        targetNodeRole = targetNodeRole == null ? "" : targetNodeRole.strip();
        targetNodeTitle = targetNodeTitle == null ? "" : targetNodeTitle.strip();
        nodeContext = nodeContext == null ? "" : nodeContext.strip();
    }
}
