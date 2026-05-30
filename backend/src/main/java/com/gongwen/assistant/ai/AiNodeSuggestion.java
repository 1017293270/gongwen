package com.gongwen.assistant.ai;

public record AiNodeSuggestion(
        Long nodeId,
        String nodeRole,
        String nodeTitle,
        String action,
        String suggestedText
) {
    public AiNodeSuggestion {
        nodeRole = nodeRole == null ? "" : nodeRole.strip();
        nodeTitle = nodeTitle == null ? "" : nodeTitle.strip();
        action = action == null || action.isBlank() ? "CREATE" : action.strip();
        suggestedText = suggestedText == null ? "" : suggestedText.strip();
    }
}
