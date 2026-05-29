package com.gongwen.assistant.exporting;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.exporting.word.ExportFormattingContext;
import com.gongwen.assistant.template.TemplateRepository;
import com.gongwen.assistant.template.TemplateSummary;
import com.gongwen.assistant.template.TemplateVersion;
import com.gongwen.assistant.template.TemplateVersionRepository;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
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
    private static final String PLACEHOLDER_TITLE = "\u6807\u9898";
    private static final String PLACEHOLDER_RECIPIENT = "\u4e3b\u9001";
    private static final String PLACEHOLDER_ATTACHMENT = "\u9644\u4ef6";
    private static final String PLACEHOLDER_SIGNATURE = "\u843d\u6b3e";
    private static final String PLACEHOLDER_DATE = "\u65e5\u671f";
    private static final String PLACEHOLDER_BODY = "\u6b63\u6587";

    private final DraftRepository draftRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateRepository templateRepository;
    private final TemplateProfileRepository templateProfileRepository;
    private final TemplateStructureFormattingRepository templateStructureFormattingRepository;
    private final TemplateEffectiveFormattingService templateEffectiveFormattingService;
    private final WordExportService wordExportService;

    public DraftWordExportService(
            DraftRepository draftRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateRepository templateRepository,
            TemplateProfileRepository templateProfileRepository,
            TemplateStructureFormattingRepository templateStructureFormattingRepository,
            TemplateEffectiveFormattingService templateEffectiveFormattingService,
            WordExportService wordExportService
    ) {
        this.draftRepository = draftRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.templateRepository = templateRepository;
        this.templateProfileRepository = templateProfileRepository;
        this.templateStructureFormattingRepository = templateStructureFormattingRepository;
        this.templateEffectiveFormattingService = templateEffectiveFormattingService;
        this.wordExportService = wordExportService;
    }

    public WordExportResult exportDraft(long draftId) {
        DraftDetailDto draft = draftRepository.findById(draftId);
        Long templateVersionId = draft.templateVersionId();
        if (templateVersionId == null) {
            throw new WordExportException(
                    "TEMPLATE_VERSION_REQUIRED",
                    "\u8bf7\u5148\u9009\u62e9\u5957\u7248\u6a21\u677f\u540e\u518d\u5bfc\u51fa Word",
                    null
            );
        }

        TemplateVersion version = templateVersionRepository.findById(templateVersionId)
                .orElseThrow(() -> new WordExportException(
                        "TEMPLATE_VERSION_NOT_FOUND",
                        "\u6240\u9009\u6a21\u677f\u7248\u672c\u4e0d\u5b58\u5728",
                        null
                ));
        if (!"READY".equals(version.parseStatus())) {
            throw new WordExportException(
                    "TEMPLATE_VERSION_NOT_READY",
                    "\u6240\u9009\u6a21\u677f\u7248\u672c\u5c1a\u672a\u89e3\u6790\u5b8c\u6210\uff0c\u4e0d\u80fd\u5bfc\u51fa",
                    null
            );
        }

        TemplateSummary template = templateRepository.findById(version.templateId())
                .orElse(new TemplateSummary(version.templateId(), "\u516c\u6587\u6a21\u677f", draft.documentTypeCode(), "ACTIVE"));

        return wordExportService.export(readTemplateBytes(version.filePath()), new WordExportRequest(
                template.templateName(),
                version.versionNo(),
                draftValues(draft),
                exportFormattingContext(templateVersionId)
        ));
    }

    private ExportFormattingContext exportFormattingContext(long templateVersionId) {
        TemplateProfile profile = templateProfileRepository.findByTemplateVersionId(templateVersionId)
                .orElseThrow(() -> new WordExportException(
                        "TEMPLATE_PROFILE_NOT_FOUND",
                        "\u6240\u9009\u6a21\u677f\u7248\u672c\u7f3a\u5c11\u89e3\u6790\u6863\u6848\uff0c\u8bf7\u91cd\u65b0\u89e3\u6790\u6216\u4e0a\u4f20\u6a21\u677f",
                        null
                ));
        Map<String, TemplateStructureFormattingProfile> overrides =
                templateStructureFormattingRepository.findOverrides(templateVersionId);
        return templateEffectiveFormattingService.resolve(profile, overrides);
    }

    private byte[] readTemplateBytes(String filePath) {
        try {
            return Files.readAllBytes(Path.of(filePath));
        } catch (IOException exception) {
            throw new WordExportException(
                    "TEMPLATE_FILE_UNAVAILABLE",
                    "\u6a21\u677f\u6587\u4ef6\u4e0d\u53ef\u8bfb\u53d6\uff0c\u8bf7\u91cd\u65b0\u4e0a\u4f20\u6a21\u677f",
                    exception
            );
        }
    }

    private Map<String, String> draftValues(DraftDetailDto draft) {
        Map<String, String> values = new HashMap<>();
        putBlock(values, draft, "TITLE", PLACEHOLDER_TITLE, draft.title());
        putBlock(values, draft, "RECIPIENT", PLACEHOLDER_RECIPIENT, "");
        putBlock(values, draft, "ATTACHMENT", PLACEHOLDER_ATTACHMENT, "");
        putBlock(values, draft, "SIGNATURE", PLACEHOLDER_SIGNATURE, "");
        putBlock(values, draft, "DATE", PLACEHOLDER_DATE, "");

        String body = draft.blocks().stream()
                .filter(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .sorted(Comparator.comparingInt(DraftBlockDto::sortOrder))
                .map(DraftBlockDto::content)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining("\n"));
        values.put(PLACEHOLDER_BODY, body);
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
