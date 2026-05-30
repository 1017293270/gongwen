package com.gongwen.assistant.documentstructure.mapping;

public record StructureMappingItem(
        String nodeKey,
        String role,
        String slotKey,
        String status,
        String source,
        double confidence,
        String notes,
        int sortOrder
) {
    public StructureMappingItem {
        nodeKey = nodeKey == null ? "" : nodeKey.strip();
        role = role == null || role.isBlank() ? "UNKNOWN" : role.strip();
        slotKey = slotKey == null ? "" : slotKey.strip();
        status = status == null || status.isBlank() ? "SUGGESTED" : status.strip();
        source = source == null || source.isBlank() ? "USER" : source.strip();
        notes = notes == null ? "" : notes.strip();
    }
}
