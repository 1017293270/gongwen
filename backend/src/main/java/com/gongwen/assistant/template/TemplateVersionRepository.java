package com.gongwen.assistant.template;

import java.util.Optional;

public interface TemplateVersionRepository {
    TemplateVersion create(long templateId, String originalFileName, String contentType, long fileSizeBytes, String filePath);

    int nextVersionNo(long templateId);

    Optional<TemplateVersion> findById(long id);

    void markParsed(long id, String profileHash);

    void markFailed(long id, String errorCode, String errorMessage);
}
