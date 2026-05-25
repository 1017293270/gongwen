package com.gongwen.assistant.draft;

import java.util.List;

public interface DraftRepository {
    DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks);

    DraftDetailDto findById(long id);

    DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks);
}
