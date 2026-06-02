package com.gongwen.assistant.draft.node;

import java.time.Instant;

public record DraftNode(
        long id,
        long draftId,
        Long structureMappingProfileId,
        String templateNodeKey,
        String parentTemplateNodeKey,
        String nodeType,
        String role,
        String slotKey,
        String title,
        String content,
        int sortOrder,
        String status,
        DraftNodeFormatOverride formatOverride,
        DraftNodeMetadata metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public DraftNode {
        templateNodeKey = templateNodeKey == null ? "" : templateNodeKey.strip();
        parentTemplateNodeKey = parentTemplateNodeKey == null || parentTemplateNodeKey.isBlank()
                ? null
                : parentTemplateNodeKey.strip();
        nodeType = nodeType == null || nodeType.isBlank() ? "PARAGRAPH" : nodeType.strip();
        role = role == null || role.isBlank() ? "UNKNOWN" : role.strip();
        slotKey = slotKey == null ? "" : slotKey.strip();
        title = title == null ? "" : title.strip();
        content = content == null ? "" : content;
        status = status == null || status.isBlank() ? "EMPTY" : status.strip();
        formatOverride = formatOverride == null ? DraftNodeFormatOverride.empty() : formatOverride;
        metadata = metadata == null ? DraftNodeMetadata.empty() : metadata;
    }

    public DraftNode(
            long id,
            long draftId,
            Long structureMappingProfileId,
            String templateNodeKey,
            String parentTemplateNodeKey,
            String nodeType,
            String role,
            String slotKey,
            String title,
            String content,
            int sortOrder,
            String status,
            DraftNodeFormatOverride formatOverride,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                draftId,
                structureMappingProfileId,
                templateNodeKey,
                parentTemplateNodeKey,
                nodeType,
                role,
                slotKey,
                title,
                content,
                sortOrder,
                status,
                formatOverride,
                DraftNodeMetadata.empty(),
                createdAt,
                updatedAt
        );
    }
}
