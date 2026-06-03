package com.gongwen.assistant.ai;

import java.util.function.Consumer;

public interface ModelAdapter {
    String provider();

    String modelName();

    AiOutlineResponse generateOutline(OutlinePrompt prompt);

    default AiParagraphModelResponse generateParagraph(ParagraphPrompt prompt) {
        throw new ModelAdapterException("MODEL_UNSUPPORTED_TASK", "模型暂不支持正文生成");
    }

    default AiParagraphModelResponse generateParagraphCandidate(ParagraphPrompt prompt) {
        return generateParagraph(prompt);
    }

    default AiParagraphModelResponse streamParagraphCandidate(
            ParagraphPrompt prompt,
            Consumer<String> onDelta
    ) {
        AiParagraphModelResponse response = generateParagraphCandidate(prompt);
        String content = response.content();
        if (content != null && !content.isBlank()) {
            int chunkSize = 28;
            for (int start = 0; start < content.length(); start += chunkSize) {
                int end = Math.min(content.length(), start + chunkSize);
                onDelta.accept(content.substring(start, end));
            }
        }
        return response;
    }

    default AiLocalOperationModelResponse generateLocalOperation(LocalOperationPrompt prompt) {
        throw new ModelAdapterException("MODEL_UNSUPPORTED_TASK", "模型暂不支持局部改写");
    }

    default AiQualityReviewResponse generateQualityReview(QualityCheckPrompt prompt) {
        throw new ModelAdapterException("MODEL_UNSUPPORTED_TASK", "模型暂不支持质检");
    }

    default TemplateAnalysisResponse generateTemplateAnalysis(TemplateAnalysisPrompt prompt) {
        throw new ModelAdapterException("MODEL_UNSUPPORTED_TASK", "模型暂不支持模板识别");
    }
}
