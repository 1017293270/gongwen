package com.gongwen.assistant.draft;

import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Service
public class DraftService {
    private final DraftRepository draftRepository;
    private final CurrentUserProvider currentUserProvider;

    @Autowired
    public DraftService(DraftRepository draftRepository, CurrentUserProvider currentUserProvider) {
        this.draftRepository = draftRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public DraftService(DraftRepository draftRepository) {
        this.draftRepository = draftRepository;
        this.currentUserProvider = null;
    }

    public DraftDetailDto createDraft(CreateDraftRequest request) {
        String documentTypeCode = request.documentTypeCode() == null || request.documentTypeCode().isBlank()
                ? "NOTICE"
                : request.documentTypeCode();
        String title = request.title() == null || request.title().isBlank()
                ? "未命名草稿"
                : request.title();
        return draftRepository.createDraft(documentTypeCode, title, List.of(), currentUserOrNull());
    }

    public DraftDetailDto getDraft(long id) {
        return draftRepository.findById(id, currentUserOrNull());
    }

    public List<DraftSummaryDto> listDrafts(String documentTypeCode) {
        String normalizedDocumentTypeCode = documentTypeCode == null || documentTypeCode.isBlank()
                ? "NOTICE"
                : documentTypeCode;
        return draftRepository.listByDocumentType(normalizedDocumentTypeCode, currentUserOrNull());
    }

    public DraftDetailDto updateBlocks(long id, UpdateDraftBlocksRequest request) {
        return draftRepository.replaceBlocks(id, request.blocks(), currentUserOrNull());
    }

    public DraftDetailDto updateTitle(long id, UpdateDraftTitleRequest request) {
        String title = request == null || request.title() == null || request.title().isBlank()
                ? "未命名草稿"
                : request.title().strip();
        return draftRepository.updateTitle(id, title, currentUserOrNull());
    }

    public DraftDetailDto updateTemplateVersion(long id, UpdateDraftTemplateRequest request) {
        return draftRepository.updateTemplateVersion(id, request == null ? null : request.templateVersionId(), currentUserOrNull());
    }

    public void deleteDraft(long id) {
        draftRepository.deleteById(id, currentUserOrNull());
    }

    private CurrentUser currentUserOrNull() {
        return currentUserProvider == null ? null : currentUserProvider.currentUser();
    }
}
