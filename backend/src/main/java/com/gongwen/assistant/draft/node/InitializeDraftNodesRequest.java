package com.gongwen.assistant.draft.node;

public record InitializeDraftNodesRequest(String bodyContentMode) {
    public boolean shouldUseTemplateHeadingsOnly() {
        return "TEMPLATE_HEADINGS_ONLY".equalsIgnoreCase(bodyContentMode == null ? "" : bodyContentMode.strip());
    }
}
