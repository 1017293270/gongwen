package com.gongwen.assistant.exporting;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DraftWordExportService {
    private static final String PLACEHOLDER_TITLE = "\u6807\u9898";
    private static final String PLACEHOLDER_RECIPIENT = "\u4e3b\u9001";
    private static final String PLACEHOLDER_ATTACHMENT = "\u9644\u4ef6";
    private static final String PLACEHOLDER_SIGNATURE = "\u843d\u6b3e";
    private static final String PLACEHOLDER_DATE = "\u65e5\u671f";
    private static final String PLACEHOLDER_BODY = "\u6b63\u6587";
    private static final Pattern CHINESE_DATE_LINE_PATTERN = Pattern.compile("^\\d{4}\u5e74\\d{1,2}\u6708\\d{1,2}\u65e5$");

    private final DraftRepository draftRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateRepository templateRepository;
    private final TemplateProfileRepository templateProfileRepository;
    private final TemplateStructureFormattingRepository templateStructureFormattingRepository;
    private final TemplateEffectiveFormattingService templateEffectiveFormattingService;
    private final WordExportService wordExportService;
    private final CurrentUserProvider currentUserProvider;

    public DraftWordExportService(
            DraftRepository draftRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateRepository templateRepository,
            TemplateProfileRepository templateProfileRepository,
            TemplateStructureFormattingRepository templateStructureFormattingRepository,
            TemplateEffectiveFormattingService templateEffectiveFormattingService,
            WordExportService wordExportService
    ) {
        this(
                draftRepository,
                templateVersionRepository,
                templateRepository,
                templateProfileRepository,
                templateStructureFormattingRepository,
                templateEffectiveFormattingService,
                wordExportService,
                null
        );
    }

    @Autowired
    public DraftWordExportService(
            DraftRepository draftRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateRepository templateRepository,
            TemplateProfileRepository templateProfileRepository,
            TemplateStructureFormattingRepository templateStructureFormattingRepository,
            TemplateEffectiveFormattingService templateEffectiveFormattingService,
            WordExportService wordExportService,
            CurrentUserProvider currentUserProvider
    ) {
        this.draftRepository = draftRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.templateRepository = templateRepository;
        this.templateProfileRepository = templateProfileRepository;
        this.templateStructureFormattingRepository = templateStructureFormattingRepository;
        this.templateEffectiveFormattingService = templateEffectiveFormattingService;
        this.wordExportService = wordExportService;
        this.currentUserProvider = currentUserProvider;
    }

    public WordExportResult exportDraft(long draftId) {
        DraftDetailDto draft = draftRepository.findById(draftId, currentUserOrNull());
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

        TemplateProfile profile = templateProfile(templateVersionId);

        return wordExportService.export(readTemplateBytes(version.filePath()), new WordExportRequest(
                template.templateName(),
                version.versionNo(),
                draftValues(draft),
                exportFormattingContext(templateVersionId, profile),
                profile
        ));
    }

    private CurrentUser currentUserOrNull() {
        return currentUserProvider == null ? null : currentUserProvider.currentUser();
    }

    private TemplateProfile templateProfile(long templateVersionId) {
        return templateProfileRepository.findByTemplateVersionId(templateVersionId)
                .orElseThrow(() -> new WordExportException(
                        "TEMPLATE_PROFILE_NOT_FOUND",
                        "\u6240\u9009\u6a21\u677f\u7248\u672c\u7f3a\u5c11\u89e3\u6790\u6863\u6848\uff0c\u8bf7\u91cd\u65b0\u89e3\u6790\u6216\u4e0a\u4f20\u6a21\u677f",
                        null
                ));
    }

    private ExportFormattingContext exportFormattingContext(long templateVersionId, TemplateProfile profile) {
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
        String title = blockValue(draft, "TITLE", draft.title());
        String recipient = blockValue(draft, "RECIPIENT", "");
        String attachment = blockValue(draft, "ATTACHMENT", "");
        String signature = blockValue(draft, "SIGNATURE", "");
        String date = blockValue(draft, "DATE", "");

        List<String> bodyLines = draft.blocks().stream()
                .filter(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .sorted(Comparator.comparingInt(DraftBlockDto::sortOrder))
                .map(DraftBlockDto::content)
                .filter(content -> content != null && !content.isBlank())
                .flatMap(content -> content.lines())
                .toList();
        CleanedBody cleanedBody = cleanBodyLines(bodyLines, recipient, attachment, signature, date);

        putValue(values, "TITLE", PLACEHOLDER_TITLE, title);
        putValue(values, "RECIPIENT", PLACEHOLDER_RECIPIENT, firstNonBlank(recipient, cleanedBody.recipient()));
        putValue(values, "ATTACHMENT", PLACEHOLDER_ATTACHMENT, firstNonBlank(attachment, cleanedBody.attachment()));
        putValue(values, "SIGNATURE", PLACEHOLDER_SIGNATURE, firstNonBlank(signature, cleanedBody.signature()));
        putValue(values, "DATE", PLACEHOLDER_DATE, firstNonBlank(date, cleanedBody.date()));
        putValue(values, "BODY_PARAGRAPH", PLACEHOLDER_BODY, cleanedBody.body());
        return values;
    }

    private String blockValue(DraftDetailDto draft, String blockType, String fallback) {
        return draft.blocks().stream()
                .filter(block -> blockType.equals(block.blockType()))
                .findFirst()
                .map(DraftBlockDto::content)
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback);
    }

    private CleanedBody cleanBodyLines(
            List<String> bodyLines,
            String explicitRecipient,
            String explicitAttachment,
            String explicitSignature,
            String explicitDate
    ) {
        List<String> cleanedLines = new ArrayList<>();
        String recipient = "";
        String attachment = "";
        for (String line : bodyLines) {
            String normalized = line.strip();
            if (normalized.isBlank()) {
                cleanedLines.add(line);
                continue;
            }
            if (isRecipientLine(normalized)) {
                if (isBlank(explicitRecipient) && recipient.isBlank()) {
                    recipient = stripTrailingColon(normalized);
                }
                continue;
            }
            if (isAttachmentLine(normalized)) {
                if (isBlank(explicitAttachment) && attachment.isBlank()) {
                    attachment = normalized;
                }
                continue;
            }
            cleanedLines.add(line);
        }

        String signature = "";
        String date = "";
        int dateIndex = lastNonBlankIndex(cleanedLines);
        if (dateIndex >= 0 && isDateLine(cleanedLines.get(dateIndex).strip())) {
            int signatureIndex = previousNonBlankIndex(cleanedLines, dateIndex);
            if (signatureIndex >= 0 && isLikelySignatureLine(cleanedLines.get(signatureIndex).strip())) {
                if (isBlank(explicitDate)) {
                    date = cleanedLines.get(dateIndex).strip();
                }
                if (isBlank(explicitSignature)) {
                    signature = cleanedLines.get(signatureIndex).strip();
                }
                cleanedLines.remove(dateIndex);
                cleanedLines.remove(signatureIndex);
            }
        }

        String body = cleanedLines.stream()
                .collect(Collectors.joining("\n"))
                .strip();
        return new CleanedBody(body, recipient, attachment, signature, date);
    }

    private boolean isRecipientLine(String line) {
        return line.length() <= 80
                && (line.endsWith("\uff1a") || line.endsWith(":"))
                && !line.contains("\uff0c")
                && !line.contains(",")
                && !line.contains("\u3002")
                && !line.contains("\uff1b")
                && !line.contains(";")
                && (line.contains("\u90e8\u95e8") || line.contains("\u5355\u4f4d") || line.contains("\u673a\u5173"));
    }

    private boolean isAttachmentLine(String line) {
        return line.startsWith("\u9644\u4ef6\uff1a") || line.startsWith("\u9644\u4ef6:");
    }

    private boolean isDateLine(String line) {
        return CHINESE_DATE_LINE_PATTERN.matcher(line).matches();
    }

    private boolean isLikelySignatureLine(String line) {
        return line.length() <= 40
                && !isRecipientLine(line)
                && !isAttachmentLine(line)
                && !isDateLine(line);
    }

    private int lastNonBlankIndex(List<String> lines) {
        return previousNonBlankIndex(lines, lines.size());
    }

    private int previousNonBlankIndex(List<String> lines, int beforeIndex) {
        for (int index = beforeIndex - 1; index >= 0; index--) {
            if (!lines.get(index).isBlank()) {
                return index;
            }
        }
        return -1;
    }

    private String stripTrailingColon(String value) {
        return value.replaceAll("[:\uff1a]+$", "");
    }

    private String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? fallback : preferred;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void putValue(Map<String, String> values, String blockType, String placeholder, String content) {
        String normalizedContent = content == null ? "" : content;
        values.put(placeholder, normalizedContent);
        values.put(blockType, normalizedContent);
    }

    private record CleanedBody(String body, String recipient, String attachment, String signature, String date) {
    }
}
