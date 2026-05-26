package com.gongwen.assistant.ai;

import java.util.UUID;

public record AiLocalOperationResponse(
        UUID traceId,
        long targetBlockId,
        AiLocalOperationType operationType,
        String suggestionText
) {
}
