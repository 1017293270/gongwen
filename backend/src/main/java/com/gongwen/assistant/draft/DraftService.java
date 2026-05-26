package com.gongwen.assistant.draft;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DraftService {
    private final DraftRepository draftRepository;
    private final Clock clock;

    @Autowired
    public DraftService(DraftRepository draftRepository) {
        this(draftRepository, Clock.systemDefaultZone());
    }

    DraftService(DraftRepository draftRepository, Clock clock) {
        this.draftRepository = draftRepository;
        this.clock = clock;
    }

    public DraftDetailDto createDraft(CreateDraftRequest request) {
        String documentTypeCode = request.documentTypeCode() == null || request.documentTypeCode().isBlank()
                ? "NOTICE"
                : request.documentTypeCode();
        String title = request.title() == null || request.title().isBlank()
                ? "关于开展年度档案整理工作的通知"
                : request.title();
        return draftRepository.createDraft(documentTypeCode, title, defaultBlocks(title));
    }

    public DraftDetailDto getDraft(long id) {
        return draftRepository.findById(id);
    }

    public DraftDetailDto updateBlocks(long id, UpdateDraftBlocksRequest request) {
        return draftRepository.replaceBlocks(id, request.blocks());
    }

    public DraftDetailDto updateTemplateVersion(long id, UpdateDraftTemplateRequest request) {
        return draftRepository.updateTemplateVersion(id, request == null ? null : request.templateVersionId());
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
