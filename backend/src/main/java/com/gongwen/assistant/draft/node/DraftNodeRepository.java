package com.gongwen.assistant.draft.node;

import java.util.List;
import java.util.Optional;

public interface DraftNodeRepository {
    List<DraftNode> findByDraftId(long draftId);

    boolean existsByDraftId(long draftId);

    List<DraftNode> replaceForDraft(long draftId, List<DraftNode> nodes);

    Optional<DraftNode> updateContent(long draftId, long nodeId, String content, String status);

    default Optional<DraftNode> updateFormatOverride(long draftId, long nodeId, DraftNodeFormatOverride override, String status) {
        throw new UnsupportedOperationException("updateFormatOverride is not implemented");
    }
}
