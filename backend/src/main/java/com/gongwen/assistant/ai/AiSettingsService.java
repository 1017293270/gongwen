package com.gongwen.assistant.ai;

import org.springframework.stereotype.Service;

@Service
public class AiSettingsService {
    private final AiConfigurationState configurationState;
    private final DeepSeekModelAdapter deepSeekModelAdapter;

    public AiSettingsService(AiConfigurationState configurationState, DeepSeekModelAdapter deepSeekModelAdapter) {
        this.configurationState = configurationState;
        this.deepSeekModelAdapter = deepSeekModelAdapter;
    }

    public AiProviderSettings getSettings() {
        return configurationState.currentSettings();
    }

    public AiProviderSettings updateSettings(AiProviderSettingsUpdateRequest request) {
        return configurationState.update(request);
    }

    public AiProviderStatus testConnection() {
        if (!configurationState.useDeepSeek()) {
            AiProviderSettings settings = configurationState.currentSettings();
            return new AiProviderStatus(settings.provider(), "mock-outline-v1", true, "当前使用 Mock 模型，无需外部连接", 0);
        }
        return deepSeekModelAdapter.testConnection();
    }
}
