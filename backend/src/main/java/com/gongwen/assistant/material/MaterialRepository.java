package com.gongwen.assistant.material;

import java.util.List;

public interface MaterialRepository {
    MaterialDto save(MaterialSaveCommand command);

    List<MaterialDto> findByDraftId(long draftId);
}
