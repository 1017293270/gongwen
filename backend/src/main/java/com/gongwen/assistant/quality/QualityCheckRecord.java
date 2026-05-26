package com.gongwen.assistant.quality;

import java.time.Instant;
import java.util.UUID;

public record QualityCheckRecord(
        UUID id,
        long draftId,
        String status,
        boolean exportBlocked,
        QualityCheckResponse result,
        UUID aiTraceId,
        Instant createdAt
) {
}
