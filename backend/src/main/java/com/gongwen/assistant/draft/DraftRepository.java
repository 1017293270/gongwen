package com.gongwen.assistant.draft;

import java.util.List;

public interface DraftRepository {
    DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks);

    default List<DraftSummaryDto> listByDocumentType(String documentTypeCode) {
        return List.of();
    }

    DraftDetailDto findById(long id);

    DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks);

    default DraftDetailDto updateTitle(long id, String title) {
        throw new UnsupportedOperationException("Draft title update is not supported");
    }

    default DraftDetailDto updateTemplateVersion(long id, Long templateVersionId) {
        throw new UnsupportedOperationException("Template version binding is not supported");
    }

    default void deleteById(long id) {
        throw new UnsupportedOperationException("Draft deletion is not supported");
    }
}
