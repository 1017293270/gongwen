package com.gongwen.assistant.template;

import java.util.List;

public interface TemplateRepository {
    TemplateSummary create(String templateName, String documentTypeCode);

    List<TemplateSummary> findAll(String documentTypeCode);
}
