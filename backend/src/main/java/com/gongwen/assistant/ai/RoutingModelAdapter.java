package com.gongwen.assistant.ai;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Primary
@Component
public class RoutingModelAdapter implements ModelAdapter {
    private final AiConfigurationState configurationState;
    private final MockModelAdapter mockModelAdapter;
    private final DeepSeekModelAdapter deepSeekModelAdapter;

    public RoutingModelAdapter(
            AiConfigurationState configurationState,
            MockModelAdapter mockModelAdapter,
            DeepSeekModelAdapter deepSeekModelAdapter
    ) {
        this.configurationState = configurationState;
        this.mockModelAdapter = mockModelAdapter;
        this.deepSeekModelAdapter = deepSeekModelAdapter;
    }

    @Override
    public String provider() {
        return activeAdapter().provider();
    }

    @Override
    public String modelName() {
        return activeAdapter().modelName();
    }

    @Override
    public AiOutlineResponse generateOutline(OutlinePrompt prompt) {
        return activeAdapter().generateOutline(prompt);
    }

    @Override
    public AiParagraphModelResponse generateParagraph(ParagraphPrompt prompt) {
        return activeAdapter().generateParagraph(prompt);
    }

    @Override
    public AiParagraphModelResponse generateParagraphCandidate(ParagraphPrompt prompt) {
        return activeAdapter().generateParagraphCandidate(prompt);
    }

    @Override
    public AiParagraphModelResponse streamParagraphCandidate(
            ParagraphPrompt prompt,
            Consumer<String> onDelta
    ) {
        return activeAdapter().streamParagraphCandidate(prompt, onDelta);
    }

    @Override
    public AiLocalOperationModelResponse generateLocalOperation(LocalOperationPrompt prompt) {
        return activeAdapter().generateLocalOperation(prompt);
    }

    @Override
    public AiQualityReviewResponse generateQualityReview(QualityCheckPrompt prompt) {
        return activeAdapter().generateQualityReview(prompt);
    }

    @Override
    public TemplateAnalysisResponse generateTemplateAnalysis(TemplateAnalysisPrompt prompt) {
        return activeAdapter().generateTemplateAnalysis(prompt);
    }

    private ModelAdapter activeAdapter() {
        return configurationState.useDeepSeek() ? deepSeekModelAdapter : mockModelAdapter;
    }
}
