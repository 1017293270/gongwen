package com.gongwen.assistant.exporting;

import com.gongwen.assistant.exporting.word.DocxTemplateRenderer;
import com.gongwen.assistant.exporting.word.MissingTemplateValueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WordExportService {
    private static final String MISSING_TEMPLATE_VALUE = "MISSING_TEMPLATE_VALUE";
    private static final String RENDER_FAILED = "WORD_EXPORT_RENDER_FAILED";

    private final ExportRecordRepository exportRecordRepository;
    private final DocxTemplateRenderer renderer;

    @Autowired
    public WordExportService(ExportRecordRepository exportRecordRepository) {
        this(exportRecordRepository, new DocxTemplateRenderer());
    }

    public WordExportService(ExportRecordRepository exportRecordRepository, DocxTemplateRenderer renderer) {
        this.exportRecordRepository = exportRecordRepository;
        this.renderer = renderer;
    }

    public WordExportResult export(byte[] templateBytes, WordExportRequest request) {
        String fileName = buildFileName(request);
        try {
            byte[] content = renderer.hasPlaceholders(templateBytes)
                    ? renderer.render(templateBytes, request.values(), request.formatting())
                    : renderer.renderReferenceDraft(
                            templateBytes,
                            request.values(),
                            request.formatting(),
                            request.templateProfile());
            exportRecordRepository.save(ExportRecord.success(request.templateName(), request.templateVersion(), fileName));
            return new WordExportResult(fileName, content);
        } catch (MissingTemplateValueException exception) {
            throw recordFailure(request, fileName, MISSING_TEMPLATE_VALUE, exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw recordFailure(request, fileName, RENDER_FAILED, normalizeRenderFailureMessage(exception), exception);
        }
    }

    private String buildFileName(WordExportRequest request) {
        String safeName = request.templateName() == null || request.templateName().isBlank()
                ? "公文模板"
                : request.templateName().replaceAll("[\\\\/:*?\"<>|]", "_");
        int version = request.templateVersion() <= 0 ? 1 : request.templateVersion();
        return safeName + "-v" + version + ".docx";
    }

    private WordExportException recordFailure(
            WordExportRequest request,
            String fileName,
            String errorCode,
            String message,
            RuntimeException cause
    ) {
        exportRecordRepository.save(ExportRecord.failure(
                request.templateName(),
                request.templateVersion(),
                fileName,
                errorCode,
                message));
        return new WordExportException(errorCode, message, cause);
    }

    private String normalizeRenderFailureMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Word export rendering failed";
        }
        return message;
    }
}
