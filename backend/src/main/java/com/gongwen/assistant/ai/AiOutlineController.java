package com.gongwen.assistant.ai;

import com.gongwen.assistant.common.api.ApiResponse;
import com.gongwen.assistant.draft.DraftNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drafts/{draftId}/ai")
public class AiOutlineController {
    private final AiOutlineService aiOutlineService;

    public AiOutlineController(AiOutlineService aiOutlineService) {
        this.aiOutlineService = aiOutlineService;
    }

    @PostMapping("/outline")
    public ApiResponse<AiOutlineResponse> generateOutline(
            @PathVariable long draftId,
            @RequestBody(required = false) AiOutlineRequest request
    ) {
        return ApiResponse.ok(aiOutlineService.generateOutline(draftId, request));
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDraftNotFound(DraftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("DRAFT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(AiOutlineException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiOutlineException(AiOutlineException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "AI_OUTLINE_INSTRUCTION_TOO_LONG" -> HttpStatus.BAD_REQUEST;
            case "AI_MODEL_UNAVAILABLE", "AI_RESPONSE_INVALID" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }
}
