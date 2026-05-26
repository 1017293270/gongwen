package com.gongwen.assistant.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DeepSeekModelAdapter implements ModelAdapter {
    private final AiConfigurationState configurationState;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DeepSeekModelAdapter(AiConfigurationState configurationState, ObjectMapper objectMapper) {
        this(configurationState, objectMapper, HttpClient.newHttpClient());
    }

    DeepSeekModelAdapter(AiConfigurationState configurationState, ObjectMapper objectMapper, HttpClient httpClient) {
        this.configurationState = configurationState;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String provider() {
        return "deepseek";
    }

    @Override
    public String modelName() {
        return configurationState.deepSeekRuntimeConfig().model();
    }

    @Override
    public AiOutlineResponse generateOutline(OutlinePrompt prompt) {
        DeepSeekOutlinePayload payload = postJson(
                List.of(
                        Map.of("role", "system", "content", systemPrompt("你负责生成严肃、克制、结构化的中文公文提纲。")),
                        Map.of("role", "user", "content", outlineUserPrompt(prompt))
                ),
                DeepSeekOutlinePayload.class
        );
        return new AiOutlineResponse(
                UUID.randomUUID(),
                payload.titleSuggestion(),
                payload.sections().stream()
                        .map(section -> new AiOutlineSection(section.heading(), section.points()))
                        .toList(),
                payload.missingInformation()
        );
    }

    @Override
    public AiParagraphModelResponse generateParagraph(ParagraphPrompt prompt) {
        DeepSeekParagraphPayload payload = postJson(
                List.of(
                        Map.of("role", "system", "content", systemPrompt("你负责生成单个严肃中文公文正文段落。")),
                        Map.of("role", "user", "content", paragraphUserPrompt(prompt))
                ),
                DeepSeekParagraphPayload.class
        );
        return new AiParagraphModelResponse(payload.content());
    }

    @Override
    public AiLocalOperationModelResponse generateLocalOperation(LocalOperationPrompt prompt) {
        DeepSeekLocalOperationPayload payload = postJson(
                List.of(
                        Map.of("role", "system", "content", systemPrompt("你负责对单个中文公文正文段落给出局部修改建议。")),
                        Map.of("role", "user", "content", localOperationUserPrompt(prompt))
                ),
                DeepSeekLocalOperationPayload.class
        );
        return new AiLocalOperationModelResponse(payload.suggestionText());
    }

    public AiProviderStatus testConnection() {
        long startedAt = System.currentTimeMillis();
        DeepSeekRuntimeConfig config = configurationState.deepSeekRuntimeConfig();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new ModelAdapterException("AI_DEEPSEEK_API_KEY_REQUIRED", "请先配置 DeepSeek API Key");
        }
        postJson(
                List.of(
                        Map.of("role", "system", "content", "你只返回 JSON。"),
                        Map.of("role", "user", "content", "请返回 {\"content\":\"ok\"}")
                ),
                DeepSeekParagraphPayload.class
        );
        return new AiProviderStatus(provider(), modelName(), true, "DeepSeek 连接正常", System.currentTimeMillis() - startedAt);
    }

    private <T> T postJson(List<Map<String, String>> messages, Class<T> payloadClass) {
        DeepSeekRuntimeConfig config = configurationState.deepSeekRuntimeConfig();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new ModelAdapterException("AI_DEEPSEEK_API_KEY_REQUIRED", "请先配置 DeepSeek API Key");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", config.model(),
                    "messages", messages,
                    "stream", false,
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object")
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelAdapterException("AI_DEEPSEEK_HTTP_ERROR", "DeepSeek 返回 HTTP " + response.statusCode());
            }
            DeepSeekChatResponse chatResponse = objectMapper.readValue(response.body(), DeepSeekChatResponse.class);
            String content = chatResponse.firstContent();
            if (content.isBlank()) {
                throw new ModelAdapterException("AI_DEEPSEEK_EMPTY_RESPONSE", "DeepSeek 返回内容为空");
            }
            return objectMapper.readValue(content, payloadClass);
        } catch (IOException exception) {
            throw new ModelAdapterException("AI_DEEPSEEK_RESPONSE_INVALID", "DeepSeek 返回结构无效");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelAdapterException("AI_DEEPSEEK_INTERRUPTED", "DeepSeek 调用被中断");
        } catch (IllegalArgumentException exception) {
            throw new ModelAdapterException("AI_DEEPSEEK_REQUEST_INVALID", "DeepSeek 请求配置无效");
        }
    }

    private String systemPrompt(String task) {
        return task + "必须只返回合法 JSON，不要输出 Markdown。不要编造材料中不存在的事实。";
    }

    private String outlineUserPrompt(OutlinePrompt prompt) {
        return """
                请基于以下信息生成公文提纲，返回 JSON：
                {
                  "titleSuggestion": "标题建议",
                  "sections": [{"heading": "一、...", "points": ["要点"]}],
                  "missingInformation": ["缺失信息"]
                }
                文种：%s
                标题：%s
                字段摘要：%s
                材料摘要：%s
                补充要求：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.title(),
                prompt.fieldSummaries(),
                prompt.materialSummaries(),
                prompt.instruction()
        );
    }

    private String paragraphUserPrompt(ParagraphPrompt prompt) {
        return """
                请基于以下信息生成一个公文正文段落，返回 JSON：
                {"content":"正文段落"}
                要求：
                1. content 必须以“段落标题”原文开头，不要省略、改写或另造标题。
                2. content 只生成这一段，不要生成其他提纲章节，也不要解释生成过程。
                3. 如果段落标题已经带有序号，直接保留该序号；不要新增第二套序号。
                4. 正文应承接标题，语气严肃克制，事实只能来自字段摘要、材料摘要和补充要求。
                文种：%s
                标题：%s
                段落标题：%s
                段落要点：%s
                字段摘要：%s
                材料摘要：%s
                补充要求：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.title(),
                prompt.heading(),
                prompt.points(),
                prompt.fieldSummaries(),
                prompt.materialSummaries(),
                prompt.instruction()
        );
    }

    private String localOperationUserPrompt(LocalOperationPrompt prompt) {
        return """
                请对一个公文正文段落执行局部操作，返回 JSON：
                {"suggestionText":"修改建议文本"}
                要求：只返回修改后的段落文本，不解释过程，不新增材料中不存在的事实。
                文种：%s
                标题：%s
                操作类型：%s
                原段落：%s
                字段摘要：%s
                材料摘要：%s
                补充要求：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.title(),
                prompt.operationType(),
                prompt.originalText(),
                prompt.fieldSummaries(),
                prompt.materialSummaries(),
                prompt.instruction()
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekChatResponse(List<DeepSeekChoice> choices) {
        private String firstContent() {
            if (choices == null || choices.isEmpty() || choices.getFirst().message() == null || choices.getFirst().message().content() == null) {
                return "";
            }
            return choices.getFirst().message().content();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekChoice(DeepSeekMessage message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekMessage(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekOutlinePayload(
            String titleSuggestion,
            List<DeepSeekOutlineSectionPayload> sections,
            List<String> missingInformation
    ) {
        private DeepSeekOutlinePayload {
            sections = sections == null ? List.of() : sections;
            missingInformation = missingInformation == null ? List.of() : missingInformation;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekOutlineSectionPayload(String heading, List<String> points) {
        private DeepSeekOutlineSectionPayload {
            points = points == null ? List.of() : points;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekParagraphPayload(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekLocalOperationPayload(String suggestionText) {
    }
}
