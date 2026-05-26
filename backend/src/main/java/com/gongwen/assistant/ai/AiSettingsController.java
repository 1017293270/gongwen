package com.gongwen.assistant.ai;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/settings")
public class AiSettingsController {
    private final AiSettingsService aiSettingsService;

    public AiSettingsController(AiSettingsService aiSettingsService) {
        this.aiSettingsService = aiSettingsService;
    }

    @GetMapping
    public ApiResponse<AiProviderSettings> getSettings() {
        return ApiResponse.ok(aiSettingsService.getSettings());
    }

    @PutMapping
    public ApiResponse<AiProviderSettings> updateSettings(@RequestBody AiProviderSettingsUpdateRequest request) {
        return ApiResponse.ok(aiSettingsService.updateSettings(request));
    }

    @PostMapping("/test")
    public ApiResponse<AiProviderStatus> testConnection() {
        return ApiResponse.ok(aiSettingsService.testConnection());
    }

    @ExceptionHandler(AiSettingsException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiSettingsException(AiSettingsException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(ModelAdapterException.class)
    public ResponseEntity<ApiResponse<Void>> handleModelAdapterException(ModelAdapterException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }
}
