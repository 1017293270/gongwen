package com.gongwen.assistant.documentstructure.mapping;

import java.time.Instant;
import java.util.List;

public record StructureMappingProfile(
        Long mappingProfileId,
        long templateVersionId,
        int versionNo,
        String status,
        List<StructureMappingItem> items,
        List<StructureMappingValidationItem> validationItems,
        int confirmedCount,
        int needsReviewCount,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public StructureMappingProfile {
        status = status == null || status.isBlank() ? "DRAFT" : status;
        items = items == null ? List.of() : List.copyOf(items);
        validationItems = validationItems == null ? List.of() : List.copyOf(validationItems);
        confirmedCount = (int) items.stream().filter(item -> "CONFIRMED".equals(item.status())).count();
        needsReviewCount = (int) items.stream().filter(item -> "NEEDS_REVIEW".equals(item.status())).count();
    }
}
