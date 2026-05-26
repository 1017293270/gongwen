package com.gongwen.assistant.ai;

import java.time.Instant;
import java.util.UUID;

public record AiGenerationTrace(
        UUID id,
        long draftId,
        String taskType,
        String provider,
        String modelName,
        String status,
        String promptVersion,
        String inputSummary,
        String outputSummary,
        String errorCode,
        String errorMessage,
        long latencyMs,
        Instant createdAt
) {
}
