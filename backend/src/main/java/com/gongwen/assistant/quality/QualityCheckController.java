package com.gongwen.assistant.quality;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drafts/{draftId}/quality-check")
@PreAuthorize("hasAnyRole('DRAFTER', 'TEMPLATE_ADMIN', 'SYSTEM_ADMIN')")
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
                .orElseThrow(() -> new QualityCheckException(
                        "QUALITY_CHECK_NOT_FOUND",
                        "Latest quality check result is not available"
                )));
    }
}
