package com.gongwen.assistant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiSettingsServiceTest {
    @Test
    void defaultsToMockWithoutPersistingSecrets() {
        AiSettingsService service = newService("");

        AiProviderSettings settings = service.getSettings();

        assertThat(settings.provider()).isEqualTo("mock");
        assertThat(settings.deepSeekApiKeyConfigured()).isFalse();
        assertThat(settings.maskedDeepSeekApiKey()).isEmpty();
    }

    @Test
    void updatesDeepSeekRuntimeSettingsAndMasksApiKey() {
        AiSettingsService service = newService("");

        AiProviderSettings settings = service.updateSettings(new AiProviderSettingsUpdateRequest(
                "deepseek",
                true,
                "https://api.deepseek.com/",
                "deepseek-v4-flash",
                "sk-1234567890",
                false,
                45
        ));

        assertThat(settings.provider()).isEqualTo("deepseek");
        assertThat(settings.deepSeekBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(settings.deepSeekModel()).isEqualTo("deepseek-v4-flash");
        assertThat(settings.deepSeekApiKeyConfigured()).isTrue();
        assertThat(settings.maskedDeepSeekApiKey()).isEqualTo("sk-1...7890");
        assertThat(settings.deepSeekTimeoutSeconds()).isEqualTo(45);
    }

    @Test
    void rejectsEnabledDeepSeekWithoutApiKey() {
        AiSettingsService service = newService("");

        assertThatThrownBy(() -> service.updateSettings(new AiProviderSettingsUpdateRequest(
                "deepseek",
                true,
                "https://api.deepseek.com",
                "deepseek-v4-pro",
                "",
                false,
                60
        ))).isInstanceOf(AiSettingsException.class)
                .hasMessage("启用 DeepSeek 时必须配置 API Key");
    }

    private AiSettingsService newService(String apiKey) {
        AiRuntimeProperties properties = new AiRuntimeProperties(
                "mock",
                new AiRuntimeProperties.DeepSeek(false, "https://api.deepseek.com", "deepseek-v4-flash", apiKey, 60)
        );
        AiConfigurationState state = new AiConfigurationState(properties);
        return new AiSettingsService(state, new DeepSeekModelAdapter(state, new ObjectMapper()));
    }
}
