package com.gongwen.assistant.ai.candidate;

import com.gongwen.assistant.common.api.ApiResponse;
import com.gongwen.assistant.draft.DraftNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/drafts/{draftId}/ai/paragraph-candidates")
public class AiParagraphCandidateController {
    private final AiParagraphCandidateService service;
    private final ParagraphCandidateJobService jobService;

    public AiParagraphCandidateController(
            AiParagraphCandidateService service,
            ParagraphCandidateJobService jobService
    ) {
        this.service = service;
        this.jobService = jobService;
    }

    @GetMapping
    public ApiResponse<List<AiParagraphCandidateDto>> list(@PathVariable long draftId) {
        return ApiResponse.ok(service.listCandidates(draftId));
    }

    @PostMapping("/batch")
    public ApiResponse<List<AiParagraphCandidateDto>> createBatch(
            @PathVariable long draftId,
            @RequestBody(required = false) CreateParagraphCandidateBatchRequest request
    ) {
        return ApiResponse.ok(service.createBatch(draftId, request));
    }

    @PostMapping("/jobs")
    public ApiResponse<ParagraphCandidateJobResponse> createJob(
            @PathVariable long draftId,
            @RequestBody(required = false) CreateParagraphCandidateBatchRequest request
    ) {
        return ApiResponse.ok(jobService.createJob(draftId, request));
    }

    @GetMapping(value = "/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamJob(
            @PathVariable long draftId,
            @PathVariable UUID jobId
    ) {
        return jobService.streamJob(draftId, jobId);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public ApiResponse<ParagraphCandidateJobResponse> cancelJob(
            @PathVariable long draftId,
            @PathVariable UUID jobId
    ) {
        return ApiResponse.ok(jobService.cancel(draftId, jobId));
    }

    @PutMapping("/{candidateId}")
    public ApiResponse<AiParagraphCandidateDto> updateText(
            @PathVariable long draftId,
            @PathVariable long candidateId,
            @RequestBody(required = false) UpdateParagraphCandidateRequest request
    ) {
        return ApiResponse.ok(service.updateText(draftId, candidateId, request));
    }

    @DeleteMapping("/{candidateId}")
    public ApiResponse<AiParagraphCandidateDto> discard(
            @PathVariable long draftId,
            @PathVariable long candidateId
    ) {
        return ApiResponse.ok(service.discard(draftId, candidateId));
    }

    @PostMapping("/{candidateId}/retry")
    public ApiResponse<AiParagraphCandidateDto> retry(
            @PathVariable long draftId,
            @PathVariable long candidateId
    ) {
        return ApiResponse.ok(service.retry(draftId, candidateId));
    }

    @PostMapping("/{candidateId}/accept")
    public ApiResponse<AiParagraphCandidateAcceptResponse> accept(
            @PathVariable long draftId,
            @PathVariable long candidateId
    ) {
        return ApiResponse.ok(service.accept(draftId, candidateId));
    }

    @PostMapping("/accept-batch")
    public ApiResponse<AiParagraphCandidateBatchAcceptResponse> acceptBatch(
            @PathVariable long draftId,
            @RequestBody(required = false) AcceptParagraphCandidateBatchRequest request
    ) {
        return ApiResponse.ok(service.acceptBatch(draftId, request));
    }

    @ExceptionHandler(AiParagraphCandidateException.class)
    public ResponseEntity<ApiResponse<Void>> handleCandidateException(AiParagraphCandidateException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "AI_CANDIDATE_NOT_FOUND",
                 "AI_CANDIDATE_DRAFT_MISMATCH",
                 "AI_CANDIDATE_TARGET_NOT_FOUND",
                 "AI_CANDIDATE_JOB_NOT_FOUND",
                 "AI_CANDIDATE_JOB_DRAFT_MISMATCH" -> HttpStatus.NOT_FOUND;
            case "AI_CANDIDATE_TARGET_BLOCKED",
                 "AI_CANDIDATE_STATUS_PROTECTED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDraftNotFound(DraftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error("DRAFT_NOT_FOUND", exception.getMessage()));
    }
}
