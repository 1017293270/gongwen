package com.gongwen.assistant.document;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {
    private final DocumentTypeService documentTypeService;

    public DocumentTypeController(DocumentTypeService documentTypeService) {
        this.documentTypeService = documentTypeService;
    }

    @GetMapping
    public ApiResponse<List<DocumentTypeDto>> listDocumentTypes() {
        return ApiResponse.ok(documentTypeService.findActive());
    }

    @PostMapping
    public ApiResponse<DocumentTypeDto> createDocumentType(@RequestBody CreateDocumentTypeRequest request) {
        return ApiResponse.ok(documentTypeService.create(request));
    }

    @PutMapping("/{code}")
    public ApiResponse<DocumentTypeDto> updateDocumentType(
            @PathVariable String code,
            @RequestBody UpdateDocumentTypeRequest request
    ) {
        return ApiResponse.ok(documentTypeService.update(code, request));
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> deleteDocumentType(@PathVariable String code) {
        documentTypeService.delete(code);
        return ApiResponse.ok(null);
    }

    @ExceptionHandler(DocumentTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleDocumentTypeException(DocumentTypeException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }
}
