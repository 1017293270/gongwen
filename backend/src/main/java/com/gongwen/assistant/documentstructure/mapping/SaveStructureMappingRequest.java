package com.gongwen.assistant.documentstructure.mapping;

import java.util.List;

public record SaveStructureMappingRequest(
        Long baseMappingProfileId,
        List<StructureMappingItem> items
) {
    public SaveStructureMappingRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
