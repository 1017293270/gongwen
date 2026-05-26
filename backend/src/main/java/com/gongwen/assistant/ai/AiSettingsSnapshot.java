package com.gongwen.assistant.ai;

public record AiSettingsSnapshot(
        String provider,
        boolean deepSeekEnabled,
        String deepSeekBaseUrl,
        String deepSeekModel,
        String deepSeekApiKey,
        int deepSeekTimeoutSeconds
) {
}
