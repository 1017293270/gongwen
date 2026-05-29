package com.gongwen.assistant.template.profile;

import java.util.Map;

public interface TemplateStructureFormattingRepository {
    Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId);

    void saveOverride(long templateVersionId, String structureKey, TemplateStructureFormattingProfile formatting);
}
