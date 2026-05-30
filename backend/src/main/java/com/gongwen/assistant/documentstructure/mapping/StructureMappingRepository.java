package com.gongwen.assistant.documentstructure.mapping;

import com.gongwen.assistant.security.CurrentUser;

import java.util.Optional;

public interface StructureMappingRepository {
    StructureMappingProfile save(StructureMappingProfile profile, CurrentUser currentUser);

    Optional<StructureMappingProfile> findLatest(long templateVersionId);

    Optional<StructureMappingProfile> findLatestByStatus(long templateVersionId, String status);

    int nextVersionNo(long templateVersionId);
}
