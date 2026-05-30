package com.gongwen.assistant.documentstructure;

import java.util.Optional;

public interface DocumentStructureProfileRepository {
    void save(long templateVersionId, DocumentStructureProfile profile);

    Optional<DocumentStructureProfile> findByTemplateVersionId(long templateVersionId);
}
