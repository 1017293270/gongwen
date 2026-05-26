package com.gongwen.assistant.ai;

public interface ModelAdapter {
    String provider();

    String modelName();

    AiOutlineResponse generateOutline(OutlinePrompt prompt);

    default AiParagraphModelResponse generateParagraph(ParagraphPrompt prompt) {
        throw new ModelAdapterException("MODEL_UNSUPPORTED_TASK", "模型暂不支持正文生成");
    }
}
