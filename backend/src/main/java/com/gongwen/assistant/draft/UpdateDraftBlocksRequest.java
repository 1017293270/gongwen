package com.gongwen.assistant.draft;

import java.util.List;

public record UpdateDraftBlocksRequest(List<DraftBlockUpdateRequest> blocks) {
    public UpdateDraftBlocksRequest {
        blocks = List.copyOf(blocks);
    }
}
