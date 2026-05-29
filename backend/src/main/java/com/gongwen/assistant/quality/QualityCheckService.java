package com.gongwen.assistant.quality;

import com.gongwen.assistant.ai.AiGenerationTrace;
import com.gongwen.assistant.ai.AiGenerationTraceRepository;
import com.gongwen.assistant.ai.AiQualityReviewResponse;
import com.gongwen.assistant.ai.AiQualitySuggestion;
import com.gongwen.assistant.ai.MaterialPromptSummary;
import com.gongwen.assistant.ai.ModelAdapter;
import com.gongwen.assistant.ai.ModelAdapterException;
import com.gongwen.assistant.ai.PromptBuilder;
import com.gongwen.assistant.ai.QualityCheckPrompt;
import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.exporting.word.ExportFormattingContext;
import com.gongwen.assistant.material.MaterialRepository;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplatePlaceholderProfile;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import com.gongwen.assistant.template.profile.TemplateValidationItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class QualityCheckService {
    private static final List<String> REQUIRED_BLOCK_TYPES = List.of("TITLE", "RECIPIENT", "SIGNATURE", "DATE");

    private final DraftService draftService;
    private final MaterialRepository materialRepository;
    private final PromptBuilder promptBuilder;
    private final ModelAdapter modelAdapter;
    private final AiGenerationTraceRepository traceRepository;
    private final QualityCheckRepository qualityCheckRepository;
    private final TemplateProfileRepository templateProfileRepository;
    private final TemplateStructureFormattingRepository templateStructureFormattingRepository;
    private final TemplateEffectiveFormattingService templateEffectiveFormattingService;

    @Autowired
    public QualityCheckService(
            DraftService draftService,
            MaterialRepository materialRepository,
            PromptBuilder promptBuilder,
            ModelAdapter modelAdapter,
            AiGenerationTraceRepository traceRepository,
            QualityCheckRepository qualityCheckRepository,
            TemplateProfileRepository templateProfileRepository,
            TemplateStructureFormattingRepository templateStructureFormattingRepository,
            TemplateEffectiveFormattingService templateEffectiveFormattingService
    ) {
        this.draftService = draftService;
        this.materialRepository = materialRepository;
        this.promptBuilder = promptBuilder;
        this.modelAdapter = modelAdapter;
        this.traceRepository = traceRepository;
        this.qualityCheckRepository = qualityCheckRepository;
        this.templateProfileRepository = templateProfileRepository;
        this.templateStructureFormattingRepository = templateStructureFormattingRepository;
        this.templateEffectiveFormattingService = templateEffectiveFormattingService;
    }

    public QualityCheckResponse runCheck(long draftId) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        List<MaterialPromptSummary> materials = materialRepository.findReadyTextSummariesByDraftId(draftId);
        List<QualityCheckItem> items = new ArrayList<>(ruleItems(draft, materials));
        items.addAll(templateItems(draft));
        QualityCheckPrompt prompt = promptBuilder.buildQualityCheckPrompt(draft, materials, ruleSummaries(items));
        UUID aiTraceId = UUID.randomUUID();
        Instant startedAt = Instant.now();

        try {
            AiQualityReviewResponse aiResponse = modelAdapter.generateQualityReview(prompt);
            List<AiQualitySuggestion> suggestions = requireAiSuggestions(aiResponse);
            List<QualityCheckItem> aiItems = suggestions.stream()
                    .map(this::toQualityItem)
                    .toList();
            items.addAll(aiItems);
            traceRepository.save(successTrace(aiTraceId, draftId, prompt, aiItems, startedAt));
        } catch (ModelAdapterException exception) {
            items.add(new QualityCheckItem(
                    "WARNING",
                    "AI_SERVICE",
                    "AI_QUALITY_UNAVAILABLE",
                    "AI 质检建议暂不可用，规则质检结果仍可参考。",
                    null,
                    null,
                    "请稍后重试 AI 质检，或先根据错误项补齐草稿。"
            ));
            traceRepository.save(failedTrace(aiTraceId, draftId, prompt, exception.errorCode(), exception.getMessage(), startedAt));
        } catch (IllegalArgumentException exception) {
            items.add(new QualityCheckItem(
                    "WARNING",
                    "AI_SERVICE",
                    "AI_QUALITY_RESPONSE_INVALID",
                    "AI 质检返回结构无效，规则质检结果仍可参考。",
                    null,
                    null,
                    "请重试质检。"
            ));
            traceRepository.save(failedTrace(aiTraceId, draftId, prompt, "AI_RESPONSE_INVALID", exception.getMessage(), startedAt));
        }

        QualityCheckResponse response = buildResponse(UUID.randomUUID(), draftId, aiTraceId, items, Instant.now());
        qualityCheckRepository.save(new QualityCheckRecord(
                response.id(),
                draftId,
                response.status(),
                response.exportBlocked(),
                response,
                aiTraceId,
                response.checkedAt()
        ));
        return response;
    }

    public Optional<QualityCheckResponse> findLatest(long draftId) {
        return qualityCheckRepository.findLatestByDraftId(draftId);
    }

    private List<QualityCheckItem> ruleItems(DraftDetailDto draft, List<MaterialPromptSummary> materials) {
        List<QualityCheckItem> items = new ArrayList<>();
        for (String blockType : REQUIRED_BLOCK_TYPES) {
            findBlock(draft, blockType)
                    .filter(block -> !isBlank(block.content()))
                    .orElseGet(() -> {
                        items.add(new QualityCheckItem(
                                "ERROR",
                                "REQUIRED_FIELD",
                                "REQUIRED_" + blockType + "_MISSING",
                                requiredMessage(blockType),
                                blockType,
                                findBlock(draft, blockType).map(DraftBlockDto::id).orElse(null),
                                "请先补齐该字段后再导出。"
                        ));
                        return null;
                    });
        }

        List<DraftBlockDto> bodyBlocks = draft.blocks().stream()
                .filter(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .sorted(Comparator.comparing(DraftBlockDto::sortOrder))
                .toList();
        if (bodyBlocks.stream().noneMatch(block -> !isBlank(block.content()))) {
            items.add(new QualityCheckItem(
                    "ERROR",
                    "STRUCTURE",
                    "BODY_PARAGRAPH_MISSING",
                    "正文内容为空。",
                    "BODY_PARAGRAPH",
                    bodyBlocks.isEmpty() ? null : bodyBlocks.getFirst().id(),
                    "请先生成或填写至少一段正文。"
            ));
        }
        if (materials.isEmpty()) {
            items.add(new QualityCheckItem(
                    "WARNING",
                    "MATERIAL",
                    "READY_MATERIAL_MISSING",
                    "当前没有可用的 READY 材料。",
                    null,
                    null,
                    "如需更可靠的事实依据，建议上传 Word/PDF 材料后重新质检。"
            ));
        }
        return items;
    }

    private List<QualityCheckItem> templateItems(DraftDetailDto draft) {
        if (draft.templateVersionId() == null) {
            return List.of(new QualityCheckItem(
                    "WARNING",
                    "TEMPLATE",
                    "TEMPLATE_VERSION_NOT_SELECTED",
                    "当前草稿尚未选择套版模板。",
                    null,
                    null,
                    "请在左侧选择一个模板版本后重新质检，才能确认正文是否能完整套版。"
            ));
        }
        TemplateProfile profile = templateProfileRepository.findByTemplateVersionId(draft.templateVersionId())
                .orElse(null);
        if (profile == null) {
            return List.of(new QualityCheckItem(
                    "ERROR",
                    "TEMPLATE",
                    "TEMPLATE_PROFILE_NOT_FOUND",
                    "当前模板版本缺少解析 Profile。",
                    null,
                    null,
                    "请重新上传或解析该模板后再导出。"
            ));
        }
        List<QualityCheckItem> items = new ArrayList<>();
        for (TemplatePlaceholderProfile placeholder : profile.placeholders()) {
            String blockType = blockTypeForPlaceholder(placeholder.key());
            if (blockType == null) {
                items.add(new QualityCheckItem(
                        "WARNING",
                        "TEMPLATE",
                        "TEMPLATE_PLACEHOLDER_UNMAPPED",
                        "模板占位符「" + placeholder.key() + "」尚未映射到草稿字段。",
                        null,
                        null,
                        "后续可在模板管理中配置字段映射；当前导出可能无法填充该占位符。"
                ));
                continue;
            }
            Optional<DraftBlockDto> block = "BODY_PARAGRAPH".equals(blockType)
                    ? draft.blocks().stream().filter(candidate -> "BODY_PARAGRAPH".equals(candidate.blockType()) && !isBlank(candidate.content())).findFirst()
                    : findBlock(draft, blockType).filter(candidate -> !isBlank(candidate.content()));
            if (block.isEmpty()) {
                items.add(new QualityCheckItem(
                        "ERROR",
                        "TEMPLATE",
                        "TEMPLATE_PLACEHOLDER_VALUE_MISSING",
                        "模板占位符「" + placeholder.key() + "」缺少可填充值。",
                        blockType,
                        findBlock(draft, blockType).map(DraftBlockDto::id).orElse(null),
                        "请补齐对应草稿内容后再导出。"
                ));
            }
            if (placeholder.splitAcrossRuns()) {
                items.add(new QualityCheckItem(
                        "WARNING",
                        "TEMPLATE",
                        "TEMPLATE_PLACEHOLDER_SPLIT_RUNS",
                        "模板占位符「" + placeholder.key() + "」跨 Word run，导出时需要结构化替换。",
                        blockType,
                        block.map(DraftBlockDto::id).orElse(null),
                        "建议后续在模板管理中确认该占位符替换效果。"
                ));
            }
        }
        for (TemplateValidationItem validationItem : profile.validationItems()) {
            items.add(new QualityCheckItem(
                    validationItem.severity(),
                    "TEMPLATE",
                    validationItem.code(),
                    validationItem.message(),
                    null,
                    null,
                    "请在模板管理中检查该模板风险。"
            ));
        }
        items.addAll(formattingRiskItems(profile, draft.templateVersionId()));
        return items;
    }

    private List<QualityCheckItem> formattingRiskItems(TemplateProfile profile, Long templateVersionId) {
        ExportFormattingContext formatting = templateEffectiveFormattingService.resolve(
                profile,
                templateVersionId == null ? java.util.Map.of() : templateStructureFormattingRepository.findOverrides(templateVersionId)
        );
        List<QualityCheckItem> items = new ArrayList<>();
        addAlignmentRisk(items, "TEMPLATE_TITLE_ALIGNMENT_RISK", formatting.title(), "CENTER", "标题", "建议在模板管理中把标题结构调整为居中。");
        addBodyIndentRisk(items, formatting.body());
        addBodySpacingRisk(items, formatting.body());
        addAlignmentRisk(items, "TEMPLATE_SIGNATURE_ALIGNMENT_RISK", formatting.signature(), "RIGHT", "落款", "建议在模板管理中把落款结构调整为右对齐。");
        addAlignmentRisk(items, "TEMPLATE_DATE_ALIGNMENT_RISK", formatting.date(), "RIGHT", "日期", "建议在模板管理中把日期结构调整为右对齐。");
        return items;
    }

    private void addAlignmentRisk(
            List<QualityCheckItem> items,
            String code,
            TemplateStructureFormattingProfile formatting,
            String expectedAlignment,
            String label,
            String suggestion
    ) {
        if (formatting == null || !expectedAlignment.equalsIgnoreCase(normalizeAlignment(formatting.alignment()))) {
            items.add(new QualityCheckItem(
                    "WARNING",
                    "TEMPLATE_FORMATTING",
                    code,
                    "模板" + label + "的有效对齐方式与当前公文规范存在风险。",
                    null,
                    null,
                    suggestion
            ));
        }
    }

    private void addBodyIndentRisk(List<QualityCheckItem> items, TemplateStructureFormattingProfile formatting) {
        if (formatting == null || formatting.indentationFirstLine() == null || formatting.indentationFirstLine() <= 0) {
            items.add(new QualityCheckItem(
                    "WARNING",
                    "TEMPLATE_FORMATTING",
                    "TEMPLATE_BODY_INDENT_RISK",
                    "模板正文的有效首行缩进缺失或不符合当前公文规范。",
                    null,
                    null,
                    "建议在模板管理中为正文结构补齐正数首行缩进。"
            ));
        }
    }

    private void addBodySpacingRisk(List<QualityCheckItem> items, TemplateStructureFormattingProfile formatting) {
        if (formatting == null || formatting.spacingBetween() == null) {
            items.add(new QualityCheckItem(
                    "WARNING",
                    "TEMPLATE_FORMATTING",
                    "TEMPLATE_BODY_SPACING_RISK",
                    "模板正文的有效行距缺失，导出后可能无法复现预期版式。",
                    null,
                    null,
                    "建议在模板管理中为正文结构补齐行距设置。"
            ));
        }
    }

    private String blockTypeForPlaceholder(String key) {
        if (key == null) {
            return null;
        }
        return switch (key.strip().toLowerCase()) {
            case "标题", "title" -> "TITLE";
            case "主送", "主送单位", "recipient" -> "RECIPIENT";
            case "正文", "正文内容", "body", "content" -> "BODY_PARAGRAPH";
            case "附件", "attachment" -> "ATTACHMENT";
            case "落款", "署名", "signature" -> "SIGNATURE";
            case "日期", "成文日期", "date" -> "DATE";
            default -> null;
        };
    }

    private QualityCheckItem toQualityItem(AiQualitySuggestion suggestion) {
        String severity = normalizeSeverity(suggestion.severity());
        String category = isBlank(suggestion.category()) ? "AI_EXPRESSION" : suggestion.category().strip();
        String code = isBlank(suggestion.code()) ? "AI_QUALITY_SUGGESTION" : suggestion.code().strip();
        String message = isBlank(suggestion.message()) ? "AI 给出了一条质检建议。" : suggestion.message().strip();
        String suggestionText = isBlank(suggestion.suggestion()) ? "" : suggestion.suggestion().strip();
        return new QualityCheckItem(severity, category, code, message, null, null, suggestionText);
    }

    private QualityCheckResponse buildResponse(UUID id, long draftId, UUID aiTraceId, List<QualityCheckItem> items, Instant checkedAt) {
        boolean hasError = items.stream().anyMatch(item -> "ERROR".equals(item.severity()));
        boolean hasWarning = items.stream().anyMatch(item -> "WARNING".equals(item.severity()));
        String status = hasError ? "ERROR" : hasWarning ? "WARNING" : "PASS";
        return new QualityCheckResponse(id, draftId, status, hasError, aiTraceId, checkedAt, items);
    }

    private List<String> ruleSummaries(List<QualityCheckItem> items) {
        return items.stream()
                .filter(item -> !"AI_SERVICE".equals(item.category()))
                .map(item -> item.severity() + ":" + item.code() + ":" + item.message())
                .toList();
    }

    private AiGenerationTrace successTrace(
            UUID traceId,
            long draftId,
            QualityCheckPrompt prompt,
            List<QualityCheckItem> aiItems,
            Instant startedAt
    ) {
        return new AiGenerationTrace(
                traceId,
                draftId,
                "QUALITY_CHECK",
                modelAdapter.provider(),
                modelAdapter.modelName(),
                "SUCCESS",
                prompt.promptVersion(),
                prompt.inputSummary(),
                "aiSuggestions=%d".formatted(aiItems.size()),
                null,
                null,
                Duration.between(startedAt, Instant.now()).toMillis(),
                Instant.now()
        );
    }

    private AiGenerationTrace failedTrace(
            UUID traceId,
            long draftId,
            QualityCheckPrompt prompt,
            String errorCode,
            String errorMessage,
            Instant startedAt
    ) {
        return new AiGenerationTrace(
                traceId,
                draftId,
                "QUALITY_CHECK",
                modelAdapter.provider(),
                modelAdapter.modelName(),
                "FAILED",
                prompt.promptVersion(),
                prompt.inputSummary(),
                null,
                errorCode,
                errorMessage,
                Duration.between(startedAt, Instant.now()).toMillis(),
                Instant.now()
        );
    }

    private Optional<DraftBlockDto> findBlock(DraftDetailDto draft, String blockType) {
        return draft.blocks().stream()
                .filter(block -> blockType.equals(block.blockType()))
                .findFirst();
    }

    private String normalizeSeverity(String severity) {
        if ("ERROR".equals(severity) || "WARNING".equals(severity) || "INFO".equals(severity)) {
            return severity;
        }
        return "INFO";
    }

    private String requiredMessage(String blockType) {
        return switch (blockType) {
            case "TITLE" -> "标题不能为空。";
            case "RECIPIENT" -> "主送对象不能为空。";
            case "SIGNATURE" -> "落款不能为空。";
            case "DATE" -> "日期不能为空。";
            default -> blockType + " 不能为空。";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeAlignment(String alignment) {
        return alignment == null ? "" : alignment.strip();
    }

    private List<AiQualitySuggestion> requireAiSuggestions(AiQualityReviewResponse aiResponse) {
        if (aiResponse == null || aiResponse.suggestions() == null) {
            throw new IllegalArgumentException("AI quality response suggestions must not be null");
        }
        return aiResponse.suggestions();
    }
}
