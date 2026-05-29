package com.gongwen.assistant.exporting;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/exports")
public class WordExportController {
    private static final MediaType DOCX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final WordExportService exportService;
    private final DraftWordExportService draftWordExportService;
    private final BuiltinDocxTemplateFactory templateFactory;

    public WordExportController(
            WordExportService exportService,
            DraftWordExportService draftWordExportService,
            BuiltinDocxTemplateFactory templateFactory
    ) {
        this.exportService = exportService;
        this.draftWordExportService = draftWordExportService;
        this.templateFactory = templateFactory;
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

    @ExceptionHandler(WordExportException.class)
    public ResponseEntity<ApiResponse<Void>> handleWordExportException(WordExportException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }
}
