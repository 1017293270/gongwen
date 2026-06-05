package com.gongwen.assistant.exporting;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/exports")
@PreAuthorize("hasAnyRole('DRAFTER', 'TEMPLATE_ADMIN', 'SYSTEM_ADMIN')")
public class WordExportController {
    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final WordExportService exportService;
    private final DraftWordExportService draftWordExportService;
    private final ExportRecordService exportRecordService;
    private final BuiltinDocxTemplateFactory templateFactory;

    public WordExportController(
            WordExportService exportService,
            DraftWordExportService draftWordExportService,
            ExportRecordService exportRecordService,
            BuiltinDocxTemplateFactory templateFactory
    ) {
        this.exportService = exportService;
        this.draftWordExportService = draftWordExportService;
        this.exportRecordService = exportRecordService;
        this.templateFactory = templateFactory;
    }

    @GetMapping
    public ApiResponse<List<ExportRecordSummary>> listExportRecords() {
        return ApiResponse.ok(exportRecordService.listRecords());
    }

    @GetMapping("/{recordId}")
    public ApiResponse<ExportRecordDetail> getExportRecord(@PathVariable long recordId) {
        return ApiResponse.ok(exportRecordService.detail(recordId));
    }

    @GetMapping("/{recordId}/download")
    public ResponseEntity<byte[]> downloadExportRecord(@PathVariable long recordId) {
        ExportRecordFile file = exportRecordService.download(recordId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.content());
    }

    @PostMapping("/{recordId}/retry")
    public ResponseEntity<byte[]> retryExportRecord(@PathVariable long recordId) {
        return wordResponse(exportRecordService.retry(recordId));
    }

    @PostMapping("/word")
    public ResponseEntity<byte[]> exportWord(@RequestBody WordExportRequest request) {
        return wordResponse(exportService.export(templateFactory.createNoticeTemplate(), request));
    }

    @PostMapping("/drafts/{draftId}/word")
    public ResponseEntity<byte[]> exportDraftWord(@PathVariable long draftId) {
        return wordResponse(draftWordExportService.exportDraft(draftId));
    }

    private ResponseEntity<byte[]> wordResponse(WordExportResult result) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(result.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(DOCX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(result.content());
    }
}
