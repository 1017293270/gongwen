package com.gongwen.assistant.exporting;

import com.gongwen.assistant.security.CurrentUser;

import java.util.List;
import java.util.Optional;

public interface ExportRecordRepository {
    void save(ExportRecord record);

    default List<ExportRecordSummary> findAll(CurrentUser currentUser) {
        return List.of();
    }

    default Optional<ExportRecordFileReference> findFileById(long recordId, CurrentUser currentUser) {
        return Optional.empty();
    }
}
