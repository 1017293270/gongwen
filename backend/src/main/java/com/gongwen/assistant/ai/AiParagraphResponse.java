package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.node.DraftNodeDto;

import java.util.UUID;

public record AiParagraphResponse(
        UUID traceId,
        DraftDetailDto draft,
        DraftBlockDto block,
        DraftNodeDto node
) {
    public AiParagraphResponse(UUID traceId, DraftDetailDto draft, DraftBlockDto block) {
        this(traceId, draft, block, null);
    }
}
