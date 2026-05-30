package com.gongwen.assistant.documentstructure.mapping;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates/versions/{versionId}/structure-mapping")
public class StructureMappingController {
    private final StructureMappingService service;

    public StructureMappingController(StructureMappingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<StructureMappingProfile> getMapping(@PathVariable long versionId) {
        return ApiResponse.ok(service.getMapping(versionId));
    }

    @PutMapping("/draft")
    public ApiResponse<StructureMappingProfile> saveDraft(
            @PathVariable long versionId,
            @RequestBody SaveStructureMappingRequest request
    ) {
        return ApiResponse.ok(service.saveDraft(versionId, request));
    }

    @PostMapping("/publish")
    public ApiResponse<StructureMappingProfile> publish(
            @PathVariable long versionId,
            @RequestBody(required = false) PublishStructureMappingRequest request
    ) {
        return ApiResponse.ok(service.publish(versionId, request == null ? new PublishStructureMappingRequest(false) : request));
    }

    @ExceptionHandler(StructureMappingException.class)
    public ResponseEntity<ApiResponse<Void>> handleStructureMappingException(StructureMappingException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "STRUCTURE_MAPPING_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "DOCUMENT_STRUCTURE_PROFILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }
}
