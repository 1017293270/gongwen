package com.gongwen.assistant.template.profile;

import java.util.Optional;

public interface TemplateProfileRepository {
    void save(long templateVersionId, TemplateProfile profile, String profileHash);

    Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId);
}
