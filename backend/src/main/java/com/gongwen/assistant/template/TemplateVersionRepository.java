package com.gongwen.assistant.template;

import com.gongwen.assistant.security.CurrentUser;

import java.util.Optional;
import java.util.List;

public interface TemplateVersionRepository {
    TemplateVersion create(long templateId, String originalFileName, String contentType, long fileSizeBytes, String filePath);

    int nextVersionNo(long templateId);

    Optional<TemplateVersion> findById(long id);

    default List<TemplateVersionSummary> findReadyVersions(String documentTypeCode) {
        return List.of();
    }

    default List<TemplateVersionSummary> findReadyVersions(String documentTypeCode, CurrentUser currentUser) {
        return findReadyVersions(documentTypeCode);
    }

    void markParsed(long id, String profileHash);

    void markFailed(long id, String errorCode, String errorMessage);
}
