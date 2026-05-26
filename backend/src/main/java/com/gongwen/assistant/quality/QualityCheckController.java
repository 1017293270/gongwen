package com.gongwen.assistant.quality;

import com.gongwen.assistant.common.api.ApiResponse;
import com.gongwen.assistant.draft.DraftNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drafts/{draftId}/quality-check")
public class QualityCheckController {
    private final QualityCheckService qualityCheckService;

    public QualityCheckController(QualityCheckService qualityCheckService) {
        this.qualityCheckService = qualityCheckService;
    }

    @PostMapping
    public ApiResponse<QualityCheckResponse> runQualityCheck(@PathVariable long draftId) {
        return ApiResponse.ok(qualityCheckService.runCheck(draftId));
    }

    @GetMapping("/latest")
    public ApiResponse<QualityCheckResponse> latestQualityCheck(@PathVariable long draftId) {
        return ApiResponse.ok(qualityCheckService.findLatest(draftId)
                .orElseThrow(() -> new QualityCheckException("QUALITY_CHECK_NOT_FOUND", "还没有质检结果")));
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDraftNotFound(DraftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("DRAFT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(QualityCheckException.class)
    public ResponseEntity<ApiResponse<Void>> handleQualityCheckException(QualityCheckException exception) {
        HttpStatus status = "QUALITY_CHECK_NOT_FOUND".equals(exception.errorCode())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }
}
