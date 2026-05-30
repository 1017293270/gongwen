package com.gongwen.assistant.ai;

public record AiOutlineRequest(
        String instruction,
        Long nodeId,
        String nodeRole,
        String nodeTitle,
        String nodeContext
) {
    public AiOutlineRequest(String instruction) {
        this(instruction, null, null, null, null);
    }
}
