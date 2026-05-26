package com.gongwen.assistant.ai;

public interface ModelAdapter {
    String provider();

    String modelName();

    AiOutlineResponse generateOutline(OutlinePrompt prompt);
}
