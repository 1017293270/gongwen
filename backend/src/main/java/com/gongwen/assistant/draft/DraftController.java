package com.gongwen.assistant.draft;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {
    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping
    public ApiResponse<DraftDetailDto> createDraft(@RequestBody CreateDraftRequest request) {
        return ApiResponse.ok(draftService.createDraft(request));
    }

    @GetMapping
    public ApiResponse<List<DraftSummaryDto>> listDrafts(@RequestParam(required = false) String documentTypeCode) {
        return ApiResponse.ok(draftService.listDrafts(documentTypeCode));
    }

    @GetMapping("/{id}")
    public ApiResponse<DraftDetailDto> getDraft(@PathVariable long id) {
        return ApiResponse.ok(draftService.getDraft(id));
    }

    @PutMapping("/{id}/blocks")
    public ApiResponse<DraftDetailDto> updateBlocks(@PathVariable long id, @RequestBody UpdateDraftBlocksRequest request) {
        return ApiResponse.ok(draftService.updateBlocks(id, request));
    }

    @PutMapping("/{id}/title")
    public ApiResponse<DraftDetailDto> updateTitle(@PathVariable long id, @RequestBody UpdateDraftTitleRequest request) {
        return ApiResponse.ok(draftService.updateTitle(id, request));
    }

    @PutMapping("/{id}/template-version")
    public ApiResponse<DraftDetailDto> updateTemplateVersion(@PathVariable long id, @RequestBody(required = false) UpdateDraftTemplateRequest request) {
        return ApiResponse.ok(draftService.updateTemplateVersion(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDraft(@PathVariable long id) {
        draftService.deleteDraft(id);
        return ApiResponse.ok(null);
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDraftNotFound(DraftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("DRAFT_NOT_FOUND", exception.getMessage()));
    }
}
