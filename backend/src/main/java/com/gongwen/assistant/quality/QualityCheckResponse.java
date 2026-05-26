package com.gongwen.assistant.quality;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QualityCheckResponse(
        UUID id,
        long draftId,
        String status,
        boolean exportBlocked,
        UUID aiTraceId,
        Instant checkedAt,
        List<QualityCheckItem> items
) {
    public QualityCheckResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
