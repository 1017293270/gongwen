package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;

import java.util.UUID;

public record AiParagraphResponse(
        UUID traceId,
        DraftDetailDto draft,
        DraftBlockDto block
) {
}
