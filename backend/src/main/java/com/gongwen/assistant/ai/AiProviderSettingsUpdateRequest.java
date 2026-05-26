package com.gongwen.assistant.ai;

public record AiProviderSettingsUpdateRequest(
        String provider,
        boolean deepSeekEnabled,
        String deepSeekBaseUrl,
        String deepSeekModel,
        String deepSeekApiKey,
        Boolean clearDeepSeekApiKey,
        Integer deepSeekTimeoutSeconds
) {
}
