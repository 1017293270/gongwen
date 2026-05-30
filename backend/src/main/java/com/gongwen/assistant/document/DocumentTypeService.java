package com.gongwen.assistant.document;

import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class DocumentTypeService {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z0-9_]+");

    private final DocumentTypeRepository repository;
    private final CurrentUserProvider currentUserProvider;

    public DocumentTypeService(DocumentTypeRepository repository) {
        this(repository, null);
    }

    @Autowired
    public DocumentTypeService(DocumentTypeRepository repository, CurrentUserProvider currentUserProvider) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<DocumentTypeDto> findActive() {
        CurrentUser currentUser = currentUserOrNull();
        return currentUser == null ? repository.findActive() : repository.findVisible(currentUser);
    }

    public DocumentTypeDto create(CreateDocumentTypeRequest request) {
        String code = normalizeCode(request == null ? null : request.code());
        String name = normalizeName(request == null ? null : request.name());
        int sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();
        CurrentUser currentUser = currentUserOrNull();
        return currentUser == null
                ? repository.create(code, name, sortOrder)
                : repository.create(code, name, sortOrder, currentUser);
    }

    public DocumentTypeDto update(String code, UpdateDocumentTypeRequest request) {
        String normalizedCode = normalizeCode(code);
        String name = normalizeName(request == null ? null : request.name());
        int sortOrder = request == null || request.sortOrder() == null ? 0 : request.sortOrder();
        CurrentUser currentUser = currentUserOrNull();
        return (currentUser == null
                ? repository.update(normalizedCode, name, sortOrder)
                : repository.update(normalizedCode, name, sortOrder, currentUser))
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
        CurrentUser currentUser = currentUserOrNull();
        boolean deleted = currentUser == null
                ? repository.delete(normalizedCode)
                : repository.delete(normalizedCode, currentUser);
        if (!deleted) {
            throw new DocumentTypeException("DOCUMENT_TYPE_NOT_FOUND", "Document type not found");
        }
    }

    private CurrentUser currentUserOrNull() {
        return currentUserProvider == null ? null : currentUserProvider.currentUser();
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
