package com.gongwen.assistant.exporting;

import com.gongwen.assistant.exporting.word.DocxTemplateRenderer;
import com.gongwen.assistant.exporting.word.MissingTemplateValueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WordExportService {
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
                    ? renderer.render(templateBytes, request.values())
                    : renderer.renderDraftSnapshot(request.values());
            exportRecordRepository.save(ExportRecord.success(request.templateName(), request.templateVersion(), fileName));
            return new WordExportResult(fileName, content);
        } catch (MissingTemplateValueException exception) {
            exportRecordRepository.save(ExportRecord.failure(
                    request.templateName(),
                    request.templateVersion(),
                    fileName,
                    "MISSING_TEMPLATE_VALUE",
                    exception.getMessage()));
            throw new WordExportException("MISSING_TEMPLATE_VALUE", exception.getMessage(), exception);
        }
    }

    private String buildFileName(WordExportRequest request) {
        String safeName = request.templateName() == null || request.templateName().isBlank()
                ? "公文模板"
                : request.templateName().replaceAll("[\\\\/:*?\"<>|]", "_");
        int version = request.templateVersion() <= 0 ? 1 : request.templateVersion();
        return safeName + "-v" + version + ".docx";
    }
}
