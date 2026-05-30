package com.gongwen.assistant.exporting;

import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class ExportRecordService {
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ExportRecordRepository exportRecordRepository;
    private final DraftWordExportService draftWordExportService;
    private final CurrentUserProvider currentUserProvider;
    private final Path storageDir;

    public ExportRecordService(
            ExportRecordRepository exportRecordRepository,
            DraftWordExportService draftWordExportService,
            CurrentUserProvider currentUserProvider,
            @Value("${gongwen.export.storage-dir:storage/exports}") String storageDir
    ) {
        this.exportRecordRepository = exportRecordRepository;
        this.draftWordExportService = draftWordExportService;
        this.currentUserProvider = currentUserProvider;
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public List<ExportRecordSummary> listRecords() {
        return exportRecordRepository.findAll(currentUser());
    }

    public ExportRecordFile download(long recordId) {
        ExportRecordFileReference reference = exportRecordRepository.findFileById(recordId, currentUser())
                .orElseThrow(() -> new ExportRecordException(
                        "EXPORT_RECORD_NOT_FOUND",
                        "导出记录不存在或无权访问"
                ));
        Path filePath = availableFilePath(reference);
        if (filePath == null) {
            throw new ExportRecordException("EXPORT_FILE_UNAVAILABLE", "导出文件不可读取");
        }
        try {
            return new ExportRecordFile(reference.fileName(), DOCX_CONTENT_TYPE, Files.readAllBytes(filePath));
        } catch (IOException exception) {
            throw new ExportRecordException("EXPORT_FILE_UNAVAILABLE", "导出文件不可读取", exception);
        }
    }

    public ExportRecordDetail detail(long recordId) {
        CurrentUser currentUser = currentUser();
        ExportRecordDetail detail = exportRecordRepository.findDetailById(recordId, currentUser)
                .orElseThrow(() -> new ExportRecordException(
                        "EXPORT_RECORD_NOT_FOUND",
                        "导出记录不存在或无权访问"
                ));
        boolean fileAvailable = exportRecordRepository.findFileById(recordId, currentUser)
                .map(this::availableFilePath)
                .isPresent();
        return detail.withFileAvailable(fileAvailable);
    }

    public WordExportResult retry(long recordId) {
        ExportRecordDetail detail = detail(recordId);
        if (!detail.canRetry() || detail.draftId() == null) {
            throw new ExportRecordException("EXPORT_RETRY_UNAVAILABLE", "该导出记录不能重试");
        }
        return draftWordExportService.exportDraft(detail.draftId());
    }

    private Path availableFilePath(ExportRecordFileReference reference) {
        Path filePath = Path.of(reference.filePath()).toAbsolutePath().normalize();
        if (!filePath.startsWith(storageDir) || !Files.isRegularFile(filePath)) {
            return null;
        }
        return filePath;
    }

    private CurrentUser currentUser() {
        return currentUserProvider.currentUser();
    }
}
