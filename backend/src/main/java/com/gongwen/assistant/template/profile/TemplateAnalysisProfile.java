package com.gongwen.assistant.template.profile;

import java.util.List;

public record TemplateAnalysisProfile(
        String templateKind,
        double confidence,
        String documentTypeCode,
        List<String> inferredFields,
        List<TemplatePlaceholderSuggestionProfile> suggestedPlaceholders,
        String message,
        String source,
        String documentKind,
        List<String> reasonCodes,
        String recommendedWorkflow,
        List<String> blockingWarnings
) {
    public TemplateAnalysisProfile(
            String templateKind,
            double confidence,
            String documentTypeCode,
            List<String> inferredFields,
            List<TemplatePlaceholderSuggestionProfile> suggestedPlaceholders,
            String message,
            String source
    ) {
        this(
                templateKind,
                confidence,
                documentTypeCode,
                inferredFields,
                suggestedPlaceholders,
                message,
                source,
                normalizeDocumentKind(templateKind),
                List.of(),
                recommendedWorkflowFor(normalizeDocumentKind(templateKind)),
                blockingWarningsFor(normalizeDocumentKind(templateKind))
        );
    }

    public TemplateAnalysisProfile {
        inferredFields = inferredFields == null ? List.of() : List.copyOf(inferredFields);
        suggestedPlaceholders = suggestedPlaceholders == null ? List.of() : List.copyOf(suggestedPlaceholders);
        message = message == null ? "" : message;
        source = source == null ? "" : source;
        documentKind = normalizeDocumentKind(documentKind == null || documentKind.isBlank() ? templateKind : documentKind);
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        recommendedWorkflow = recommendedWorkflow == null || recommendedWorkflow.isBlank()
                ? recommendedWorkflowFor(documentKind)
                : recommendedWorkflow;
        blockingWarnings = blockingWarnings == null ? blockingWarningsFor(documentKind) : List.copyOf(blockingWarnings);
    }

    private static String normalizeDocumentKind(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN_DOCUMENT";
        }
        return switch (value) {
            case "STANDARD_PLACEHOLDER_TEMPLATE" -> "PLACEHOLDER_TEMPLATE";
            default -> value;
        };
    }

    private static String recommendedWorkflowFor(String documentKind) {
        return switch (documentKind) {
            case "PLACEHOLDER_TEMPLATE" -> "AUTO_TEMPLATE";
            case "STYLE_TEMPLATE", "REFERENCE_DOCUMENT", "OFFICIAL_DOCUMENT" -> "REVIEW_AND_MAP";
            case "MANUAL_OR_GUIDE", "POLICY_OR_REGULATION", "ORDINARY_DOCUMENT" -> "BLOCK_AUTO_TEMPLATE";
            default -> "REVIEW_REQUIRED";
        };
    }

    private static List<String> blockingWarningsFor(String documentKind) {
        return switch (documentKind) {
            case "MANUAL_OR_GUIDE" -> List.of("该文件更像公文使用手册或格式说明，不应直接发布为自动套版模板。");
            case "POLICY_OR_REGULATION" -> List.of("该文件更像制度或规范文本，不应直接发布为自动套版模板。");
            case "ORDINARY_DOCUMENT" -> List.of("该文件不像公文模板或范文，不应直接发布为自动套版模板。");
            default -> List.of();
        };
    }
}
