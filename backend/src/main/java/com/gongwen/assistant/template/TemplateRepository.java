package com.gongwen.assistant.template;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository {
    TemplateSummary create(String templateName, String documentTypeCode);

    List<TemplateSummary> findAll(String documentTypeCode);

    Optional<TemplateSummary> findById(long id);

    default void deleteById(long id) {
        throw new UnsupportedOperationException("Template deletion is not supported");
    }
}
