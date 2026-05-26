package com.gongwen.assistant.ai;

public record AiProviderStatus(
        String provider,
        String model,
        boolean available,
        String message,
        long latencyMs
) {
}
