package com.gongwen.assistant.exporting;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.template.TemplateRepository;
import com.gongwen.assistant.template.TemplateSummary;
import com.gongwen.assistant.template.TemplateVersion;
import com.gongwen.assistant.template.TemplateVersionRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DraftWordExportService {
    private final DraftRepository draftRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateRepository templateRepository;
    private final WordExportService wordExportService;

    public DraftWordExportService(
            DraftRepository draftRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateRepository templateRepository,
            WordExportService wordExportService
    ) {
        this.draftRepository = draftRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.templateRepository = templateRepository;
        this.wordExportService = wordExportService;
    }

    public WordExportResult exportDraft(long draftId) {
        DraftDetailDto draft = draftRepository.findById(draftId);
        if (draft.templateVersionId() == null) {
            throw new WordExportException("TEMPLATE_VERSION_REQUIRED", "请先选择套版模板后再导出 Word", null);
        }

        TemplateVersion version = templateVersionRepository.findById(draft.templateVersionId())
                .orElseThrow(() -> new WordExportException("TEMPLATE_VERSION_NOT_FOUND", "所选模板版本不存在", null));
        if (!"READY".equals(version.parseStatus())) {
            throw new WordExportException("TEMPLATE_VERSION_NOT_READY", "所选模板版本尚未解析完成，不能导出", null);
        }

        TemplateSummary template = templateRepository.findById(version.templateId())
                .orElse(new TemplateSummary(version.templateId(), "公文模板", draft.documentTypeCode(), "ACTIVE"));

        return wordExportService.export(readTemplateBytes(version.filePath()), new WordExportRequest(
                template.templateName(),
                version.versionNo(),
                draftValues(draft)
        ));
    }

    private byte[] readTemplateBytes(String filePath) {
        try {
            return Files.readAllBytes(Path.of(filePath));
        } catch (IOException exception) {
            throw new WordExportException("TEMPLATE_FILE_UNAVAILABLE", "模板文件不可读取，请重新上传模板", exception);
        }
    }

    private Map<String, String> draftValues(DraftDetailDto draft) {
        Map<String, String> values = new HashMap<>();
        putBlock(values, draft, "TITLE", "标题", draft.title());
        putBlock(values, draft, "RECIPIENT", "主送", "");
        putBlock(values, draft, "ATTACHMENT", "附件", "");
        putBlock(values, draft, "SIGNATURE", "落款", "");
        putBlock(values, draft, "DATE", "日期", "");

        String body = draft.blocks().stream()
                .filter(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .sorted(Comparator.comparingInt(DraftBlockDto::sortOrder))
                .map(DraftBlockDto::content)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining("\n"));
        values.put("正文", body);
        values.put("BODY_PARAGRAPH", body);
        return values;
    }

    private void putBlock(Map<String, String> values, DraftDetailDto draft, String blockType, String placeholder, String fallback) {
        String content = draft.blocks().stream()
                .filter(block -> blockType.equals(block.blockType()))
                .findFirst()
                .map(DraftBlockDto::content)
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback);
        values.put(placeholder, content);
        values.put(blockType, content);
    }
}
