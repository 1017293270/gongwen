package com.gongwen.assistant.ai;

import java.util.UUID;

public record AiLocalOperationResponse(
        UUID traceId,
        Long targetBlockId,
        Long targetNodeId,
        String targetNodeRole,
        AiLocalOperationType operationType,
        String suggestionText
) {
    public AiLocalOperationResponse(UUID traceId, long targetBlockId, AiLocalOperationType operationType, String suggestionText) {
        this(traceId, targetBlockId, null, "", operationType, suggestionText);
    }

    public AiLocalOperationResponse {
        targetNodeRole = targetNodeRole == null ? "" : targetNodeRole.strip();
    }
}
