package com.gongwen.assistant.draft.node;

public record DraftNodeMetadata(
        boolean synthetic,
        Long anchorNodeId,
        String anchorTemplateNodeKey,
        String insertPosition,
        String groupId,
        String styleSourceNodeKey
) {
    public DraftNodeMetadata {
        anchorTemplateNodeKey = anchorTemplateNodeKey == null ? "" : anchorTemplateNodeKey.strip();
        insertPosition = insertPosition == null ? "" : insertPosition.strip();
        groupId = groupId == null ? "" : groupId.strip();
        styleSourceNodeKey = styleSourceNodeKey == null ? "" : styleSourceNodeKey.strip();
    }

    public static DraftNodeMetadata empty() {
        return new DraftNodeMetadata(false, null, "", "", "", "");
    }

    public static DraftNodeMetadata synthetic(
            Long anchorNodeId,
            String anchorTemplateNodeKey,
            String insertPosition,
            String groupId,
            String styleSourceNodeKey
    ) {
        return new DraftNodeMetadata(true, anchorNodeId, anchorTemplateNodeKey, insertPosition, groupId, styleSourceNodeKey);
    }
}
