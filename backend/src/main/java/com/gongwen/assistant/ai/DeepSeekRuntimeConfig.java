package com.gongwen.assistant.ai;

public record DeepSeekRuntimeConfig(
        String baseUrl,
        String model,
        String apiKey,
        int timeoutSeconds
) {
}
