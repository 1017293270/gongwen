package com.gongwen.assistant.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gongwen.ai")
public record AiRuntimeProperties(
        String provider,
        DeepSeek deepseek,
        String settingsKeyFile
) {
    public AiRuntimeProperties {
        provider = provider == null || provider.isBlank() ? "mock" : provider;
        deepseek = deepseek == null ? new DeepSeek(false, "https://api.deepseek.com", "deepseek-v4-flash", "", 60) : deepseek;
        settingsKeyFile = settingsKeyFile == null || settingsKeyFile.isBlank()
                ? "storage/ai-settings.key"
                : settingsKeyFile;
    }

    public record DeepSeek(
            boolean enabled,
            String baseUrl,
            String model,
            String apiKey,
            int timeoutSeconds
    ) {
        public DeepSeek {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : trimTrailingSlash(baseUrl);
            model = model == null || model.isBlank() ? "deepseek-v4-flash" : model;
            apiKey = apiKey == null ? "" : apiKey;
            timeoutSeconds = timeoutSeconds <= 0 ? 60 : timeoutSeconds;
        }

        private static String trimTrailingSlash(String value) {
            String normalized = value.strip();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }
    }
}
