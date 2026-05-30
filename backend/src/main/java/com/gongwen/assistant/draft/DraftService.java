package com.gongwen.assistant.draft;

import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DraftService {
    private final DraftRepository draftRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    @Autowired
    public DraftService(DraftRepository draftRepository, CurrentUserProvider currentUserProvider) {
        this(draftRepository, currentUserProvider, Clock.systemDefaultZone());
    }

    public DraftService(DraftRepository draftRepository) {
        this(draftRepository, null, Clock.systemDefaultZone());
    }

    DraftService(DraftRepository draftRepository, Clock clock) {
        this(draftRepository, null, clock);
    }

    DraftService(DraftRepository draftRepository, CurrentUserProvider currentUserProvider, Clock clock) {
        this.draftRepository = draftRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    public DraftDetailDto createDraft(CreateDraftRequest request) {
        String documentTypeCode = request.documentTypeCode() == null || request.documentTypeCode().isBlank()
                ? "NOTICE"
                : request.documentTypeCode();
        String title = request.title() == null || request.title().isBlank()
                ? "关于开展年度档案整理工作的通知"
                : request.title();
        return draftRepository.createDraft(documentTypeCode, title, defaultBlocks(title), currentUserOrNull());
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

    private List<DraftBlockUpdateRequest> defaultBlocks(String title) {
        return List.of(
                new DraftBlockUpdateRequest("TITLE", title, 10),
                new DraftBlockUpdateRequest("RECIPIENT", "各部门、各直属单位", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30),
                new DraftBlockUpdateRequest("ATTACHMENT", "无", 40),
                new DraftBlockUpdateRequest("SIGNATURE", "办公室", 50),
                new DraftBlockUpdateRequest("DATE", LocalDate.now(clock).format(DateTimeFormatter.ofPattern("yyyy年M月d日")), 60)
        );
    }
}
