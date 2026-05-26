package com.gongwen.assistant.ai;

public record AiProviderSettings(
        String provider,
        boolean deepSeekEnabled,
        String deepSeekBaseUrl,
        String deepSeekModel,
        boolean deepSeekApiKeyConfigured,
        String maskedDeepSeekApiKey,
        int deepSeekTimeoutSeconds
) {
}
