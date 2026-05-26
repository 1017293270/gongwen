package com.gongwen.assistant.material;

import com.gongwen.assistant.ai.MaterialPromptSummary;

import java.util.List;

public interface MaterialRepository {
    MaterialDto save(MaterialSaveCommand command);

    List<MaterialDto> findByDraftId(long draftId);

    List<MaterialPromptSummary> findReadyTextSummariesByDraftId(long draftId);
}
