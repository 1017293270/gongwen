package com.gongwen.assistant.draft;

import java.util.List;

public interface DraftRepository {
    DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks);

    DraftDetailDto findById(long id);

    DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks);

    default DraftDetailDto updateTemplateVersion(long id, Long templateVersionId) {
        throw new UnsupportedOperationException("Template version binding is not supported");
    }
}
