package com.gongwen.assistant.ai;

public record AiLocalOperationRequest(
        Long targetBlockId,
        AiLocalOperationType operationType,
        String instruction
) {
}
