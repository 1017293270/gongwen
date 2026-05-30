package com.gongwen.assistant.template;

import com.gongwen.assistant.ai.ModelAdapter;
import com.gongwen.assistant.ai.MockModelAdapter;
import com.gongwen.assistant.ai.TemplateAnalysisPrompt;
import com.gongwen.assistant.ai.TemplateAnalysisResponse;
import com.gongwen.assistant.template.profile.TemplateAnalysisProfile;
import com.gongwen.assistant.template.profile.TemplatePlaceholderSuggestionProfile;
import com.gongwen.assistant.template.profile.TemplateProfile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class TemplateIntelligenceService {
    private static final int TEXT_SAMPLE_LIMIT = 2400;

    private final ModelAdapter modelAdapter;

    public TemplateIntelligenceService(ModelAdapter modelAdapter) {
        this.modelAdapter = modelAdapter;
    }

    public TemplateProfile enrich(String documentTypeCode, String originalFileName, byte[] content, TemplateProfile profile) {
        if (!profile.placeholders().isEmpty()) {
            return profile.withTemplateAnalysis(new TemplateAnalysisProfile(
                    "STANDARD_PLACEHOLDER_TEMPLATE",
                    1.0,
                    documentTypeCode,
                    profile.placeholders().stream().map(placeholder -> placeholder.key()).distinct().toList(),
                    List.of(),
                    "已识别到显式占位符，可直接用于自动套版。",
                    "RULE",
                    "PLACEHOLDER_TEMPLATE",
                    List.of("EXPLICIT_PLACEHOLDERS"),
                    "AUTO_TEMPLATE",
                    List.of()
            ));
        }

        String textSample = extractTextSample(content);
        TemplateAnalysisProfile ruleAnalysis = classifyByRules(documentTypeCode, originalFileName, textSample);
        if (ruleAnalysis != null) {
            return profile.withTemplateAnalysis(ruleAnalysis);
        }

        TemplateAnalysisPrompt prompt = new TemplateAnalysisPrompt(
                documentTypeCode,
                originalFileName,
                textSample,
                profile.styles().stream().map(style -> style.styleName() == null ? style.styleId() : style.styleName()).toList(),
                profile.tables().size(),
                profile.sections().stream().anyMatch(section -> section.hasHeader()),
                profile.sections().stream().anyMatch(section -> section.hasFooter())
        );

        try {
            return profile.withTemplateAnalysis(toProfile(modelAdapter.generateTemplateAnalysis(prompt)));
        } catch (RuntimeException exception) {
            return profile.withTemplateAnalysis(toProfile(new MockModelAdapter().generateTemplateAnalysis(prompt)));
        }
    }

    private TemplateAnalysisProfile toProfile(TemplateAnalysisResponse response) {
        return new TemplateAnalysisProfile(
                response.templateKind(),
                response.confidence(),
                response.documentTypeCode(),
                response.inferredFields(),
                response.suggestedPlaceholders().stream()
                        .map(suggestion -> new TemplatePlaceholderSuggestionProfile(suggestion.field(), suggestion.reason()))
                        .toList(),
                response.message(),
                response.source(),
                documentKind(response.templateKind()),
                List.of("AI_TEMPLATE_ANALYSIS"),
                recommendedWorkflow(response.templateKind()),
                blockingWarnings(documentKind(response.templateKind()))
        );
    }

    private TemplateAnalysisProfile classifyByRules(String documentTypeCode, String originalFileName, String textSample) {
        String combined = safeText(originalFileName) + "\n" + safeText(textSample);
        List<String> reasonCodes = new ArrayList<>();
        if (containsAny(combined, "手册", "指南", "培训", "写作基础", "格式说明")) {
            reasonCodes.add("MANUAL_OR_GUIDE_KEYWORD");
        }
        if (containsAny(combined, "标题：", "标题:", "正文：", "正文:", "方正小标宋", "方正仿宋", "首行缩进", "行距")) {
            reasonCodes.add("FORMAT_INSTRUCTION_TEXT");
        }
        if (reasonCodes.contains("MANUAL_OR_GUIDE_KEYWORD") && reasonCodes.contains("FORMAT_INSTRUCTION_TEXT")) {
            return new TemplateAnalysisProfile(
                    "MANUAL_OR_GUIDE",
                    0.92,
                    documentTypeCode,
                    List.of(),
                    List.of(),
                    "该文件更像公文使用手册或格式说明，适合作为知识材料或参考资料，不应直接进入自动套版模板发布流程。",
                    "RULE",
                    "MANUAL_OR_GUIDE",
                    reasonCodes,
                    "BLOCK_AUTO_TEMPLATE",
                    blockingWarnings("MANUAL_OR_GUIDE")
            );
        }
        return null;
    }

    private String documentKind(String templateKind) {
        if (templateKind == null || templateKind.isBlank()) {
            return "UNKNOWN_DOCUMENT";
        }
        return switch (templateKind) {
            case "STANDARD_PLACEHOLDER_TEMPLATE" -> "PLACEHOLDER_TEMPLATE";
            default -> templateKind;
        };
    }

    private String recommendedWorkflow(String templateKind) {
        return switch (documentKind(templateKind)) {
            case "PLACEHOLDER_TEMPLATE" -> "AUTO_TEMPLATE";
            case "STYLE_TEMPLATE" -> "REVIEW_AND_ADD_PLACEHOLDERS";
            case "REFERENCE_DOCUMENT", "OFFICIAL_DOCUMENT" -> "REVIEW_AND_MAP";
            case "MANUAL_OR_GUIDE", "POLICY_OR_REGULATION", "ORDINARY_DOCUMENT" -> "BLOCK_AUTO_TEMPLATE";
            default -> "REVIEW_REQUIRED";
        };
    }

    private List<String> blockingWarnings(String documentKind) {
        return switch (documentKind) {
            case "MANUAL_OR_GUIDE" -> List.of("该文件更像公文使用手册或格式说明，不应直接发布为自动套版模板。");
            case "POLICY_OR_REGULATION" -> List.of("该文件更像制度或规范文本，不应直接发布为自动套版模板。");
            case "ORDINARY_DOCUMENT" -> List.of("该文件不像公文模板或范文，不应直接发布为自动套版模板。");
            default -> List.of();
        };
    }

    private boolean containsAny(String text, String... keywords) {
        String normalized = safeText(text).toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String extractTextSample(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> appendSample(text, paragraph.getText()));
            document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells()
                    .forEach(cell -> appendSample(text, cell.getText()))));
            return text.length() > TEXT_SAMPLE_LIMIT ? text.substring(0, TEXT_SAMPLE_LIMIT) : text.toString();
        } catch (IOException exception) {
            return "";
        }
    }

    private void appendSample(StringBuilder text, String value) {
        if (value == null || value.isBlank() || text.length() >= TEXT_SAMPLE_LIMIT) {
            return;
        }
        text.append(value.strip()).append('\n');
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
