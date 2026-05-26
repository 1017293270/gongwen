package com.gongwen.assistant.quality;

import java.util.Optional;

public interface QualityCheckRepository {
    void save(QualityCheckRecord record);

    Optional<QualityCheckResponse> findLatestByDraftId(long draftId);
}
