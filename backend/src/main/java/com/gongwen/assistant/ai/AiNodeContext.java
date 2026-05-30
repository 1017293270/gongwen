package com.gongwen.assistant.ai;

public record AiNodeContext(
        Long nodeId,
        String nodeRole,
        String nodeTitle,
        String nodeContext
) {
    private static final int CONTEXT_LIMIT = 240;

    public AiNodeContext {
        nodeRole = nodeRole == null ? "" : nodeRole.strip();
        nodeTitle = nodeTitle == null ? "" : nodeTitle.strip();
        nodeContext = nodeContext == null ? "" : nodeContext.strip();
    }

    public static AiNodeContext none() {
        return new AiNodeContext(null, "", "", "");
    }

    public boolean present() {
        return nodeId != null || !nodeRole.isBlank() || !nodeTitle.isBlank() || !nodeContext.isBlank();
    }

    public String promptSummary() {
        if (!present()) {
            return "";
        }
        return "nodeId=%s;nodeRole=%s;nodeTitle=%s;nodeContext=%s".formatted(
                nodeId == null ? "" : nodeId,
                nodeRole,
                nodeTitle,
                summarize(nodeContext, CONTEXT_LIMIT)
        );
    }

    public String traceSummary() {
        if (!present()) {
            return "nodeId=;nodeRole=;nodeContextChars=0";
        }
        return "nodeId=%s;nodeRole=%s;nodeContextChars=%d".formatted(
                nodeId == null ? "" : nodeId,
                nodeRole,
                nodeContext.length()
        );
    }

    private String summarize(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
