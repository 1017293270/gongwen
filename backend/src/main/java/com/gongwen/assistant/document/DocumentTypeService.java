package com.gongwen.assistant.document;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class DocumentTypeService {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9_]+");

    private final DocumentTypeRepository repository;

    public DocumentTypeService(DocumentTypeRepository repository) {
        this.repository = repository;
    }

    public List<DocumentTypeDto> findActive() {
        return repository.findActive();
    }

    public DocumentTypeDto create(CreateDocumentTypeRequest request) {
        String code = normalizeCode(request == null ? null : request.code());
        String name = normalizeName(request == null ? null : request.name());
        int sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();
        return repository.create(code, name, sortOrder);
    }

    public DocumentTypeDto update(String code, UpdateDocumentTypeRequest request) {
        String normalizedCode = normalizeCode(code);
        String name = normalizeName(request == null ? null : request.name());
        int sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();
        return repository.update(normalizedCode, name, sortOrder)
                .orElseThrow(() -> new DocumentTypeException("DOCUMENT_TYPE_NOT_FOUND", "Document type not found"));
    }

    public void delete(String code) {
        String normalizedCode = normalizeCode(code);
        if (repository.countDrafts(normalizedCode) > 0) {
            throw new DocumentTypeException("DOCUMENT_TYPE_HAS_DRAFTS", "Document type still has drafts and cannot be deleted");
        }
        if (repository.countTemplates(normalizedCode) > 0) {
            throw new DocumentTypeException("DOCUMENT_TYPE_HAS_TEMPLATES", "Document type still has templates and cannot be deleted");
        }
        boolean deleted = repository.delete(normalizedCode);
        if (!deleted) {
            throw new DocumentTypeException("DOCUMENT_TYPE_NOT_FOUND", "Document type not found");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new DocumentTypeException("DOCUMENT_TYPE_CODE_REQUIRED", "Document type code is required");
        }
        String normalized = code.strip().toUpperCase();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new DocumentTypeException(
                    "DOCUMENT_TYPE_CODE_INVALID",
                    "Document type code can only contain uppercase letters, numbers, and underscores");
        }
        return normalized;
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new DocumentTypeException("DOCUMENT_TYPE_NAME_REQUIRED", "Document type name is required");
        }
        return name.strip();
    }
}
