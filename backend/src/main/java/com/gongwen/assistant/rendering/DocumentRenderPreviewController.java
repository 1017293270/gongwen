package com.gongwen.assistant.rendering;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class DocumentRenderPreviewController {
    private final DocumentRenderPreviewService service;
    private final LibreOfficeRenderClient renderClient;

    public DocumentRenderPreviewController(DocumentRenderPreviewService service, LibreOfficeRenderClient renderClient) {
        this.service = service;
        this.renderClient = renderClient;
    }

    @GetMapping("/templates/versions/{versionId}/render-preview")
    public ApiResponse<DocumentRenderPreview> getStatus(@PathVariable long versionId) {
        return ApiResponse.ok(service.getStatus(versionId));
    }

    @PostMapping("/templates/versions/{versionId}/render-preview")
    public ApiResponse<DocumentRenderPreview> requestRender(@PathVariable long versionId) {
        return ApiResponse.ok(service.requestRender(versionId));
    }

    @GetMapping("/drafts/{draftId}/render-preview")
    public ApiResponse<DocumentRenderPreview> getDraftStatus(@PathVariable long draftId) {
        return ApiResponse.ok(service.getDraftStatus(draftId));
    }

    @PostMapping("/drafts/{draftId}/render-preview")
    public ApiResponse<DocumentRenderPreview> requestDraftRender(@PathVariable long draftId) {
        return ApiResponse.ok(service.requestDraftRender(draftId));
    }

    @GetMapping("/render-previews/environment")
    public ApiResponse<RenderPreviewEnvironmentStatus> getEnvironmentStatus() {
        return ApiResponse.ok(renderClient.environmentStatus());
    }

    @GetMapping("/render-previews/{previewId}/pages/{pageNumber}")
    public ResponseEntity<byte[]> getPage(
            @PathVariable long previewId,
            @PathVariable int pageNumber
    ) {
        DocumentRenderPreviewFile file = service.getPage(previewId, pageNumber);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.content());
    }

    @ExceptionHandler(RenderPreviewException.class)
    public ResponseEntity<ApiResponse<Void>> handleRenderPreviewException(RenderPreviewException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "TEMPLATE_VERSION_NOT_FOUND", "RENDER_PREVIEW_NOT_FOUND", "RENDER_PREVIEW_PAGE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "RENDER_PREVIEW_UNSUPPORTED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }
}
