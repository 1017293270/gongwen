package com.gongwen.assistant.ai;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drafts/{draftId}/ai")
@PreAuthorize("hasAnyRole('DRAFTER', 'TEMPLATE_ADMIN', 'SYSTEM_ADMIN')")
public class AiOutlineController {
    private final AiOutlineService aiOutlineService;
    private final AiParagraphService aiParagraphService;
    private final AiLocalOperationService aiLocalOperationService;

    public AiOutlineController(
            AiOutlineService aiOutlineService,
            AiParagraphService aiParagraphService,
            AiLocalOperationService aiLocalOperationService
    ) {
        this.aiOutlineService = aiOutlineService;
        this.aiParagraphService = aiParagraphService;
        this.aiLocalOperationService = aiLocalOperationService;
    }

    @PostMapping("/outline")
    public ApiResponse<AiOutlineResponse> generateOutline(
            @PathVariable long draftId,
            @RequestBody(required = false) AiOutlineRequest request
    ) {
        return ApiResponse.ok(aiOutlineService.generateOutline(draftId, request));
    }

    @PostMapping("/paragraph")
    public ApiResponse<AiParagraphResponse> generateParagraph(
            @PathVariable long draftId,
            @RequestBody AiParagraphRequest request
    ) {
        return ApiResponse.ok(aiParagraphService.generateParagraph(draftId, request));
    }

    @PostMapping("/local-operation")
    public ApiResponse<AiLocalOperationResponse> generateLocalOperation(
            @PathVariable long draftId,
            @RequestBody AiLocalOperationRequest request
    ) {
        return ApiResponse.ok(aiLocalOperationService.generateSuggestion(draftId, request));
    }
}
