package com.gongwen.assistant.draft;

import com.gongwen.assistant.security.CurrentUser;

import java.util.List;

public interface DraftRepository {
    DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks);

    default DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks, CurrentUser currentUser) {
        return createDraft(documentTypeCode, title, blocks);
    }

    default List<DraftSummaryDto> listByDocumentType(String documentTypeCode) {
        return List.of();
    }

    default List<DraftSummaryDto> listByDocumentType(String documentTypeCode, CurrentUser currentUser) {
        return listByDocumentType(documentTypeCode);
    }

    DraftDetailDto findById(long id);

    default DraftDetailDto findById(long id, CurrentUser currentUser) {
        return findById(id);
    }

    DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks);

    default DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks, CurrentUser currentUser) {
        return replaceBlocks(id, blocks);
    }

    default DraftDetailDto updateTitle(long id, String title) {
        throw new UnsupportedOperationException("Draft title update is not supported");
    }

    default DraftDetailDto updateTitle(long id, String title, CurrentUser currentUser) {
        return updateTitle(id, title);
    }

    default DraftDetailDto updateTemplateVersion(long id, Long templateVersionId) {
        throw new UnsupportedOperationException("Template version binding is not supported");
    }

    default DraftDetailDto updateTemplateVersion(long id, Long templateVersionId, CurrentUser currentUser) {
        return updateTemplateVersion(id, templateVersionId);
    }

    default void deleteById(long id) {
        throw new UnsupportedOperationException("Draft deletion is not supported");
    }

    default void deleteById(long id, CurrentUser currentUser) {
        deleteById(id);
    }
}
