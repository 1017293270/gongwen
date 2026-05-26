package com.gongwen.assistant.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@EnableConfigurationProperties(AiRuntimeProperties.class)
public class AiConfigurationState {
    public static final String PROVIDER_MOCK = "mock";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "deepseek-v4-flash",
            "deepseek-v4-pro",
            "deepseek-chat",
            "deepseek-reasoner"
    );

    private String provider;
    private boolean deepSeekEnabled;
    private String deepSeekBaseUrl;
    private String deepSeekModel;
    private String deepSeekApiKey;
    private int deepSeekTimeoutSeconds;

    public AiConfigurationState(AiRuntimeProperties properties) {
        this.provider = normalizeProvider(properties.provider());
        this.deepSeekEnabled = properties.deepseek().enabled();
        this.deepSeekBaseUrl = normalizeBaseUrl(properties.deepseek().baseUrl());
        this.deepSeekModel = normalizeModel(properties.deepseek().model());
        this.deepSeekApiKey = properties.deepseek().apiKey();
        this.deepSeekTimeoutSeconds = properties.deepseek().timeoutSeconds();
    }

    public synchronized AiProviderSettings currentSettings() {
        return new AiProviderSettings(
                provider,
                deepSeekEnabled,
                deepSeekBaseUrl,
                deepSeekModel,
                hasDeepSeekApiKey(),
                maskApiKey(deepSeekApiKey),
                deepSeekTimeoutSeconds
        );
    }

    public synchronized AiProviderSettings update(AiProviderSettingsUpdateRequest request) {
        if (request == null) {
            throw new AiSettingsException("AI_SETTINGS_REQUEST_REQUIRED", "AI 配置不能为空");
        }
        provider = normalizeProvider(request.provider());
        deepSeekEnabled = request.deepSeekEnabled();
        deepSeekBaseUrl = normalizeBaseUrl(request.deepSeekBaseUrl());
        deepSeekModel = normalizeModel(request.deepSeekModel());
        deepSeekTimeoutSeconds = request.deepSeekTimeoutSeconds() == null || request.deepSeekTimeoutSeconds() <= 0
                ? 60
                : request.deepSeekTimeoutSeconds();
        if (Boolean.TRUE.equals(request.clearDeepSeekApiKey())) {
            deepSeekApiKey = "";
        } else if (request.deepSeekApiKey() != null && !request.deepSeekApiKey().isBlank()) {
            deepSeekApiKey = request.deepSeekApiKey().strip();
        }
        if (PROVIDER_DEEPSEEK.equals(provider) && deepSeekEnabled && !hasDeepSeekApiKey()) {
            throw new AiSettingsException("AI_DEEPSEEK_API_KEY_REQUIRED", "启用 DeepSeek 时必须配置 API Key");
        }
        return currentSettings();
    }

    public synchronized boolean useDeepSeek() {
        return PROVIDER_DEEPSEEK.equals(provider) && deepSeekEnabled && hasDeepSeekApiKey();
    }

    public synchronized DeepSeekRuntimeConfig deepSeekRuntimeConfig() {
        return new DeepSeekRuntimeConfig(
                deepSeekBaseUrl,
                deepSeekModel,
                deepSeekApiKey,
                deepSeekTimeoutSeconds
        );
    }

    private boolean hasDeepSeekApiKey() {
        return deepSeekApiKey != null && !deepSeekApiKey.isBlank();
    }

    private String normalizeProvider(String value) {
        if (value == null || value.isBlank()) {
            return PROVIDER_MOCK;
        }
        String normalized = value.strip().toLowerCase();
        if (!PROVIDER_MOCK.equals(normalized) && !PROVIDER_DEEPSEEK.equals(normalized)) {
            throw new AiSettingsException("AI_PROVIDER_UNSUPPORTED", "暂不支持的 AI 供应商");
        }
        return normalized;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank() ? "https://api.deepseek.com" : value.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            throw new AiSettingsException("AI_DEEPSEEK_BASE_URL_INVALID", "DeepSeek Base URL 必须以 http:// 或 https:// 开头");
        }
        return normalized;
    }

    private String normalizeModel(String value) {
        String normalized = value == null || value.isBlank() ? "deepseek-v4-flash" : value.strip();
        if (!SUPPORTED_MODELS.contains(normalized)) {
            throw new AiSettingsException("AI_DEEPSEEK_MODEL_UNSUPPORTED", "暂不支持的 DeepSeek 模型");
        }
        return normalized;
    }

    private String maskApiKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip();
        if (normalized.length() <= 8) {
            return "****";
        }
        return normalized.substring(0, 4) + "..." + normalized.substring(normalized.length() - 4);
    }
}
