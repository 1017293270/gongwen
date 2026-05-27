package com.gongwen.assistant.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class MockModelAdapter implements ModelAdapter {
    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String modelName() {
        return "mock-outline-v1";
    }

    @Override
    public AiOutlineResponse generateOutline(OutlinePrompt prompt) {
        List<AiOutlineSection> sections = new ArrayList<>();
        sections.add(new AiOutlineSection("一、背景与依据", List.of("概括发文背景", "结合材料提炼主要依据")));
        sections.add(new AiOutlineSection("二、主要事项", List.of("明确工作安排", "说明责任分工和时间要求")));
        sections.add(new AiOutlineSection("三、执行要求", List.of("提出落实要求", "补充报送或反馈方式")));

        List<String> missing = new ArrayList<>();
        if (isBlankField(prompt, "RECIPIENT")) {
            missing.add("主送对象");
        }
        if (prompt.materialSummaries().isEmpty()) {
            missing.add("支撑材料");
        }
        if (prompt.instruction().isBlank()) {
            missing.add("补充写作要求");
        }

        return new AiOutlineResponse(
                UUID.randomUUID(),
                prompt.title().isBlank() ? "关于有关事项的通知" : prompt.title(),
                sections,
                missing
        );
    }

    @Override
    public AiParagraphModelResponse generateParagraph(ParagraphPrompt prompt) {
        String points = prompt.points().isEmpty()
                ? "围绕" + prompt.heading() + "展开具体说明"
                : String.join("；", prompt.points());
        String instruction = prompt.instruction().isBlank() ? "" : prompt.instruction();
        return new AiParagraphModelResponse(prompt.heading() + "：" + points + "。" + instruction);
    }

    @Override
    public AiLocalOperationModelResponse generateLocalOperation(LocalOperationPrompt prompt) {
        String instruction = prompt.instruction().isBlank() ? "" : "（已结合补充要求：" + prompt.instruction() + "）";
        String suggestion = switch (prompt.operationType()) {
            case FORMALIZE -> "现就有关事项进一步明确如下：" + prompt.originalText() + instruction;
            case COMPRESS -> prompt.originalText().length() > 80
                    ? prompt.originalText().substring(0, 80) + "。" + instruction
                    : prompt.originalText() + instruction;
            case EXPAND -> prompt.originalText() + "请各相关单位结合实际细化落实举措，明确责任分工和完成时限。" + instruction;
            case REWRITE -> "为确保相关工作有序推进，" + prompt.originalText() + instruction;
            case SUPPLEMENT -> prompt.originalText() + "同时，应加强过程跟踪和结果反馈，确保工作闭环落实。" + instruction;
        };
        return new AiLocalOperationModelResponse(suggestion);
    }

    @Override
    public AiQualityReviewResponse generateQualityReview(QualityCheckPrompt prompt) {
        List<AiQualitySuggestion> suggestions = new ArrayList<>();
        if (!prompt.bodySummaries().isEmpty()) {
            suggestions.add(new AiQualitySuggestion(
                    "WARNING",
                    "AI_EXPRESSION",
                    "AI_EXPRESSION_CLARITY",
                    "建议进一步压实责任表述，避免只写原则性要求。",
                    "可补充牵头部门、完成时限和反馈方式。"
            ));
        }
        if (prompt.materialSummaries().isEmpty()) {
            suggestions.add(new AiQualitySuggestion(
                    "INFO",
                    "AI_MATERIAL",
                    "AI_MATERIAL_REFERENCE",
                    "当前未看到 READY 材料摘要，建议补充依据材料后再定稿。",
                    "上传会议纪要、制度依据或工作方案后再运行质检。"
            ));
        }
        return new AiQualityReviewResponse(suggestions);
    }

    @Override
    public TemplateAnalysisResponse generateTemplateAnalysis(TemplateAnalysisPrompt prompt) {
        String text = prompt.textSample();
        String documentType = inferDocumentType(prompt.documentTypeCode(), text);
        List<String> fields = inferFields(text);
        String kind = text.length() > 500 ? "REFERENCE_DOCUMENT" : "STYLE_TEMPLATE";
        double confidence = text.isBlank() ? 0.42 : 0.78;
        String message = switch (kind) {
            case "REFERENCE_DOCUMENT" -> "未发现占位符。该文件更像完整范文，可参考内容与格式，但不建议直接作为套版模板。";
            case "STYLE_TEMPLATE" -> "未发现占位符。该文件更像样式模板，可按建议字段补充占位符后用于自动套版。";
            default -> "未发现占位符，需要人工确认该文件是否适合作为模板。";
        };
        return new TemplateAnalysisResponse(
                kind,
                confidence,
                documentType,
                fields,
                fields.stream()
                        .map(field -> new TemplatePlaceholderSuggestion(field, "根据文档结构和公文常见字段推断"))
                        .toList(),
                message,
                "MOCK"
        );
    }

    private boolean isBlankField(OutlinePrompt prompt, String blockType) {
        return prompt.fieldSummaries().stream()
                .filter(summary -> summary.startsWith(blockType + ":"))
                .allMatch(summary -> summary.equals(blockType + ": "));
    }

    private String inferDocumentType(String requestedType, String text) {
        if (text.contains("请示")) {
            return "REQUEST";
        }
        if (text.contains("报告")) {
            return "REPORT";
        }
        return requestedType == null || requestedType.isBlank() ? "NOTICE" : requestedType;
    }

    private List<String> inferFields(String text) {
        List<String> fields = new ArrayList<>();
        fields.add("标题");
        if (text.contains("各") || text.contains("：")) {
            fields.add("主送");
        }
        fields.add("正文");
        if (text.matches("(?s).*(20\\d{2}年\\d{1,2}月\\d{1,2}日|20\\d{2}-\\d{1,2}-\\d{1,2}).*")) {
            fields.add("日期");
        }
        if (text.contains("附件")) {
            fields.add("附件");
        }
        return fields;
    }
}
