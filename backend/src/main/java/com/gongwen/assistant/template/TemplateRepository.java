package com.gongwen.assistant.template;

import com.gongwen.assistant.security.CurrentUser;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository {
    TemplateSummary create(String templateName, String documentTypeCode);

    default TemplateSummary create(String templateName, String documentTypeCode, CurrentUser currentUser) {
        return create(templateName, documentTypeCode);
    }

    List<TemplateSummary> findAll(String documentTypeCode);

    default List<TemplateSummary> findAll(String documentTypeCode, CurrentUser currentUser) {
        return findAll(documentTypeCode);
    }

    Optional<TemplateSummary> findById(long id);

    default Optional<TemplateSummary> findById(long id, CurrentUser currentUser) {
        return findById(id);
    }

    default void deleteById(long id) {
        throw new UnsupportedOperationException("Template deletion is not supported");
    }

    default void deleteById(long id, CurrentUser currentUser) {
        deleteById(id);
    }
}
