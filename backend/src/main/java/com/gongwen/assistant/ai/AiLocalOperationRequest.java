package com.gongwen.assistant.ai;

public record AiLocalOperationRequest(
        Long targetBlockId,
        Long nodeId,
        String nodeRole,
        String nodeTitle,
        String nodeContext,
        AiLocalOperationType operationType,
        String instruction
) {
    public AiLocalOperationRequest(Long targetBlockId, AiLocalOperationType operationType, String instruction) {
        this(targetBlockId, null, null, null, null, operationType, instruction);
    }
}
