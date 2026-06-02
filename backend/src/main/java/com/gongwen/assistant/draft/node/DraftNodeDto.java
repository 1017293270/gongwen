package com.gongwen.assistant.draft.node;

import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;

import java.time.Instant;

public record DraftNodeDto(
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
        TemplateStructureFormattingProfile baseFormatting,
        TemplateStructureFormattingProfile effectiveFormatting,
        DraftNodeMetadata metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public DraftNodeDto(
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
                null,
                null,
                metadata,
                createdAt,
                updatedAt
        );
    }

    public static DraftNodeDto from(DraftNode node) {
        return from(node, null, null);
    }

    public static DraftNodeDto from(
            DraftNode node,
            TemplateStructureFormattingProfile baseFormatting,
            TemplateStructureFormattingProfile effectiveFormatting
    ) {
        return new DraftNodeDto(
                node.id(),
                node.draftId(),
                node.structureMappingProfileId(),
                node.templateNodeKey(),
                node.parentTemplateNodeKey(),
                node.nodeType(),
                node.role(),
                node.slotKey(),
                node.title(),
                node.content(),
                node.sortOrder(),
                node.status(),
                node.formatOverride(),
                baseFormatting,
                effectiveFormatting,
                node.metadata(),
                node.createdAt(),
                node.updatedAt()
        );
    }
}
