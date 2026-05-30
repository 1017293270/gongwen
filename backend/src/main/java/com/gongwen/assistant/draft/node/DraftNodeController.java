package com.gongwen.assistant.draft.node;

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

@RestController
@RequestMapping("/api/drafts/{draftId}/nodes")
public class DraftNodeController {
    private final DraftNodeService service;

    public DraftNodeController(DraftNodeService service) {
        this.service = service;
    }

    @PostMapping("/initialize")
    public ApiResponse<List<DraftNodeDto>> initialize(@PathVariable long draftId) {
        return ApiResponse.ok(service.initializeNodes(draftId));
    }

    @GetMapping
    public ApiResponse<List<DraftNodeDto>> list(@PathVariable long draftId) {
        return ApiResponse.ok(service.listNodes(draftId));
    }

    @PutMapping("/{nodeId}")
    public ApiResponse<DraftNodeDto> update(
            @PathVariable long draftId,
            @PathVariable long nodeId,
            @RequestBody(required = false) UpdateDraftNodeRequest request
    ) {
        return ApiResponse.ok(service.updateNode(draftId, nodeId, request));
    }

    @PutMapping("/{nodeId}/format-override")
    public ApiResponse<DraftNodeDto> updateFormatOverride(
            @PathVariable long draftId,
            @PathVariable long nodeId,
            @RequestBody(required = false) DraftNodeFormatOverride request
    ) {
        return ApiResponse.ok(service.saveFormatOverride(draftId, nodeId, request));
    }

    @DeleteMapping("/{nodeId}/format-override")
    public ApiResponse<DraftNodeDto> restoreTemplateDefaultFormatting(
            @PathVariable long draftId,
            @PathVariable long nodeId
    ) {
        return ApiResponse.ok(service.restoreTemplateDefaultFormatting(draftId, nodeId));
    }

    @ExceptionHandler(DraftNodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleDraftNodeException(DraftNodeException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "DRAFT_NODE_NOT_FOUND", "DOCUMENT_STRUCTURE_PROFILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DRAFT_TEMPLATE_REQUIRED", "STRUCTURE_MAPPING_REQUIRED" -> HttpStatus.UNPROCESSABLE_ENTITY;
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
