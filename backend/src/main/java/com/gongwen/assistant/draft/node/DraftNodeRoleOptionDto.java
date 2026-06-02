package com.gongwen.assistant.draft.node;

public record DraftNodeRoleOptionDto(
        String role,
        String label,
        boolean createsBodyPair
) {
}
