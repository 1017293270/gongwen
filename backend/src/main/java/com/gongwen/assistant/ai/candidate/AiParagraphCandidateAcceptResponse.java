package com.gongwen.assistant.ai.candidate;

import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.node.DraftNodeDto;

public record AiParagraphCandidateAcceptResponse(
        AiParagraphCandidateDto candidate,
        DraftDetailDto draft,
        DraftNodeDto node
) {
}
