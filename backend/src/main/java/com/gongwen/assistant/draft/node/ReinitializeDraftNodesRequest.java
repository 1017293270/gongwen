package com.gongwen.assistant.draft.node;

public record ReinitializeDraftNodesRequest(
        String mode,
        Boolean preserveUserEditedNodes
) {
    public ReinitializeDraftNodesRequest {
        mode = mode == null || mode.isBlank() ? "FROM_SOURCE_DOCUMENT" : mode.strip();
        preserveUserEditedNodes = preserveUserEditedNodes == null || preserveUserEditedNodes;
    }

    public boolean shouldPreserveUserEditedNodes() {
        return Boolean.TRUE.equals(preserveUserEditedNodes);
    }
}
