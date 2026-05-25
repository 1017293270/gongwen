package com.gongwen.assistant.document;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {
    private final DocumentTypeRepository documentTypeRepository;

    public DocumentTypeController(DocumentTypeRepository documentTypeRepository) {
        this.documentTypeRepository = documentTypeRepository;
    }

    @GetMapping
    public ApiResponse<List<DocumentTypeDto>> listDocumentTypes() {
        return ApiResponse.ok(documentTypeRepository.findActive());
    }
}
