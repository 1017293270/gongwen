package com.gongwen.assistant.ai;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class DeepSeekModelAdapter implements ModelAdapter {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekModelAdapter.class);
    private static final String[] TITLE_FIELDS = {
            "titleSuggestion", "title", "title_suggestion", "suggestedTitle", "documentTitle",
            "generatedTitle", "titleText", "标题", "标题建议", "建议标题", "拟定标题", "公文标题", "文档标题"
    };
    private static final String[] SECTION_FIELDS = {
            "sections", "outline", "outlineSections", "items", "sectionList", "bodyStructure",
            "提纲", "章节", "章节列表", "提纲结构", "正文结构", "大纲", "段落"
    };
    private static final String[] SECTION_CONTAINER_FIELDS = {
            "sections", "outline", "outlineSections", "sectionList", "bodyStructure",
            "提纲", "章节", "章节列表", "提纲结构", "正文结构", "大纲", "段落"
    };
    private static final String[] OUTLINE_CONTAINER_FIELDS = {
            "result", "data", "output", "response", "outline", "提纲", "结果", "正文结构", "提纲结构"
    };
    private static final String[] HEADING_FIELDS = {
            "heading", "title", "headingText", "sectionTitle", "section_heading", "name",
            "标题", "小标题", "章节标题", "段落标题", "部分标题", "名称"
    };
    private static final String[] POINT_FIELDS = {
            "points", "items", "keyPoints", "children", "details", "content", "summary",
            "requirements", "要点", "要点列表", "写作要点", "主要内容", "内容", "说明"
    };
    private static final String[] LEVEL_FIELDS = {
            "level", "headingLevel", "sectionLevel", "titleLevel", "层级", "标题层级", "章节层级", "级别"
    };
    private static final String[] SOURCE_REF_FIELDS = {
            "sourceRefs", "sourceReferences", "sources", "references", "materialRefs", "referenceFiles",
            "参考材料", "材料引用", "来源", "依据材料", "参考文件"
    };
    private static final String[] MISSING_FIELDS = {
            "missingInformation", "missing", "missing_information", "missingInfo", "missingFields",
            "缺失信息", "缺失信息提示", "待补充信息", "补充信息", "需要补充的信息"
    };
    private static final String[] TEXT_VALUE_FIELDS = {
            "text", "value", "content", "point", "name", "内容", "要点", "文本", "名称"
    };

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
        JsonNode payload = postJson(
                List.of(
                        Map.of("role", "system", "content", systemPrompt("你负责生成严肃、克制、结构化的中文公文提纲。")),
                        Map.of("role", "user", "content", outlineUserPrompt(prompt))
                ),
                JsonNode.class
        );
        return parseOutlinePayload(prompt, payload);
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
    public AiParagraphModelResponse generateParagraphCandidate(ParagraphPrompt prompt) {
        DeepSeekParagraphPayload payload = postJson(
                List.of(
                        Map.of("role", "system", "content", systemPrompt("你负责为结构化公文工作台生成正文节点候选内容。")),
                        Map.of("role", "user", "content", paragraphCandidateUserPrompt(prompt))
                ),
                DeepSeekParagraphPayload.class
        );
        return new AiParagraphModelResponse(payload.content());
    }

    @Override
    public AiParagraphModelResponse streamParagraphCandidate(
            ParagraphPrompt prompt,
            Consumer<String> onDelta
    ) {
        String content = postPlainTextStream(
                List.of(
                        Map.of("role", "system", "content", "你负责为结构化公文工作台生成正文节点候选内容。只输出正文纯文本，不要输出 JSON、Markdown 或解释。不要编造材料中不存在的事实。"),
                        Map.of("role", "user", "content", paragraphCandidateStreamUserPrompt(prompt))
                ),
                onDelta
        );
        return new AiParagraphModelResponse(content);
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

    @Override
    public AiQualityReviewResponse generateQualityReview(QualityCheckPrompt prompt) {
        DeepSeekQualityPayload payload = postJson(
                List.of(
                        Map.of("role", "system", "content", systemPrompt("你负责审阅中文公文草稿，给出克制、可执行的质检建议。")),
                        Map.of("role", "user", "content", qualityReviewUserPrompt(prompt))
                ),
                DeepSeekQualityPayload.class
        );
        return new AiQualityReviewResponse(payload.suggestions().stream()
                .map(suggestion -> new AiQualitySuggestion(
                        suggestion.severity(),
                        suggestion.category(),
                        suggestion.code(),
                        suggestion.message(),
                        suggestion.suggestion()
                ))
                .toList());
    }

    @Override
    public TemplateAnalysisResponse generateTemplateAnalysis(TemplateAnalysisPrompt prompt) {
        DeepSeekTemplateAnalysisPayload payload = postJson(
                List.of(
                        Map.of("role", "system", "content", systemPrompt("你负责识别 Word 公文模板是否可用于自动套版。")),
                        Map.of("role", "user", "content", templateAnalysisUserPrompt(prompt))
                ),
                DeepSeekTemplateAnalysisPayload.class
        );
        return new TemplateAnalysisResponse(
                payload.templateKind(),
                payload.confidence(),
                payload.documentTypeCode(),
                payload.inferredFields(),
                payload.suggestedPlaceholders().stream()
                        .map(suggestion -> new TemplatePlaceholderSuggestion(suggestion.field(), suggestion.reason()))
                        .toList(),
                payload.message(),
                "DEEPSEEK"
        );
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
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", config.model(),
                    "messages", messages,
                    "stream", false,
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object")
            ));
        } catch (IOException exception) {
            throw new ModelAdapterException("AI_DEEPSEEK_REQUEST_INVALID", "DeepSeek 请求配置无效");
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new ModelAdapterException("AI_DEEPSEEK_REQUEST_INVALID", "DeepSeek 请求配置无效");
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException exception) {
            log.warn("DeepSeek JSON call timed out payloadClass={}", payloadClass.getSimpleName());
            throw new ModelAdapterException("AI_DEEPSEEK_TIMEOUT", "DeepSeek 调用超时，请稍后重试");
        } catch (IOException exception) {
            log.warn(
                    "DeepSeek JSON call transport failed payloadClass={} reason={}",
                    payloadClass.getSimpleName(),
                    exception.getClass().getSimpleName()
            );
            throw new ModelAdapterException("AI_DEEPSEEK_NETWORK_ERROR", "DeepSeek 连接失败，请检查网络或 Base URL");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelAdapterException("AI_DEEPSEEK_INTERRUPTED", "DeepSeek 调用被中断");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ModelAdapterException("AI_DEEPSEEK_HTTP_ERROR", "DeepSeek 返回 HTTP " + response.statusCode());
        }

        DeepSeekChatResponse chatResponse;
        try {
            chatResponse = objectMapper.readValue(response.body(), DeepSeekChatResponse.class);
        } catch (IOException exception) {
            log.warn(
                    "DeepSeek chat envelope parsing failed payloadClass={} responseChars={} reason={}",
                    payloadClass.getSimpleName(),
                    response.body() == null ? 0 : response.body().length(),
                    exception.getClass().getSimpleName()
            );
            throw new ModelAdapterException("AI_DEEPSEEK_RESPONSE_INVALID", "DeepSeek 返回结构无效");
        }

        String content = chatResponse.firstContent();
        if (content.isBlank()) {
            throw new ModelAdapterException("AI_DEEPSEEK_EMPTY_RESPONSE", "DeepSeek 返回内容为空");
        }
        return parseJsonContent(content, payloadClass, response.body().length());
    }

    private String postPlainTextStream(
            List<Map<String, String>> messages,
            Consumer<String> onDelta
    ) {
        DeepSeekRuntimeConfig config = configurationState.deepSeekRuntimeConfig();
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new ModelAdapterException("AI_DEEPSEEK_API_KEY_REQUIRED", "请先配置 DeepSeek API Key");
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", config.model(),
                    "messages", messages,
                    "stream", true,
                    "temperature", 0.2
            ));
        } catch (IOException exception) {
            throw new ModelAdapterException("AI_DEEPSEEK_REQUEST_INVALID", "DeepSeek 请求配置无效");
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new ModelAdapterException("AI_DEEPSEEK_REQUEST_INVALID", "DeepSeek 请求配置无效");
        }

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException exception) {
            log.warn("DeepSeek stream call timed out");
            throw new ModelAdapterException("AI_DEEPSEEK_TIMEOUT", "DeepSeek 调用超时，请稍后重试");
        } catch (IOException exception) {
            log.warn("DeepSeek stream call transport failed reason={}", exception.getClass().getSimpleName());
            throw new ModelAdapterException("AI_DEEPSEEK_NETWORK_ERROR", "DeepSeek 连接失败，请检查网络或 Base URL");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ModelAdapterException("AI_DEEPSEEK_INTERRUPTED", "DeepSeek 调用被中断");
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = readErrorBody(response.body());
            throw new ModelAdapterException(
                    "AI_DEEPSEEK_HTTP_ERROR",
                    errorBody.isBlank() ? "DeepSeek 返回 HTTP " + response.statusCode() : "DeepSeek 返回 HTTP " + response.statusCode()
            );
        }

        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String delta = streamDeltaFromLine(line);
                    if (delta == null) {
                        continue;
                    }
                    if (!delta.isEmpty()) {
                        content.append(delta);
                        onDelta.accept(delta);
                    }
                }
            }
            if (content.isEmpty()) {
                throw new ModelAdapterException("AI_DEEPSEEK_EMPTY_RESPONSE", "DeepSeek 返回内容为空");
            }
            return content.toString();
        } catch (IOException exception) {
            log.warn("DeepSeek stream read failed reason={}", exception.getClass().getSimpleName());
            throw new ModelAdapterException("AI_DEEPSEEK_NETWORK_ERROR", "DeepSeek 流式连接中断，请重试");
        }
    }

    private String readErrorBody(InputStream body) {
        if (body == null) {
            return "";
        }
        try {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            log.debug("DeepSeek error body read failed", exception);
            return "";
        }
    }

    AiOutlineResponse parseOutlinePayload(OutlinePrompt prompt, JsonNode payload) {
        JsonNode root = payload == null || payload.isNull()
                ? objectMapper.createObjectNode()
                : payload;
        JsonNode container = firstExisting(root, OUTLINE_CONTAINER_FIELDS);

        String title = firstNonBlank(
                firstText(root, TITLE_FIELDS),
                firstText(container, TITLE_FIELDS),
                prompt == null ? "" : prompt.title(),
                defaultTitleFor(prompt == null ? "" : prompt.documentTypeCode())
        );
        List<AiOutlineSection> sections = parseOutlineSections(root);
        if (sections.isEmpty()) {
            sections = parseOutlineSections(container);
        }
        if (sections.isEmpty()) {
            log.warn(
                    "DeepSeek outline payload parsed without sections rootShape={} rootSectionShape={} containerShape={} containerSectionShape={} promptSummary={}",
                    describeJsonShape(root),
                    describeSectionCandidateShape(root),
                    describeJsonShape(container),
                    describeSectionCandidateShape(container),
                    prompt == null ? "" : prompt.inputSummary()
            );
        }
        List<String> missingInformation = parseTextList(firstExisting(root, MISSING_FIELDS));
        if (missingInformation.isEmpty()) {
            missingInformation = parseTextList(firstExisting(container, MISSING_FIELDS));
        }

        return new AiOutlineResponse(
                UUID.randomUUID(),
                title,
                sections,
                missingInformation
        );
    }

    private <T> T parseJsonContent(String content, Class<T> payloadClass, int responseBodyChars) {
        String json = "";
        try {
            json = extractJsonObject(content);
            return objectMapper.readValue(json, payloadClass);
        } catch (ModelAdapterException exception) {
            log.warn(
                    "DeepSeek JSON extraction failed payloadClass={} responseChars={} contentChars={} contentShape={}",
                    payloadClass.getSimpleName(),
                    responseBodyChars,
                    content == null ? 0 : content.length(),
                    describeContentShape(content)
            );
            throw exception;
        } catch (IOException exception) {
            log.warn(
                    "DeepSeek JSON binding failed payloadClass={} responseChars={} contentChars={} jsonShape={} reason={}",
                    payloadClass.getSimpleName(),
                    responseBodyChars,
                    content == null ? 0 : content.length(),
                    describeExtractedJsonShape(json),
                    exception.getClass().getSimpleName()
            );
            throw new ModelAdapterException("AI_DEEPSEEK_RESPONSE_INVALID", "DeepSeek 返回结构无效");
        }
    }

    private String describeContentShape(String content) {
        if (content == null || content.isBlank()) {
            return "blank";
        }
        String normalized = stripMarkdownFence(content.strip());
        if (!normalized.startsWith("{") && !normalized.startsWith("[")) {
            return "non-json chars=" + normalized.length();
        }
        try {
            return describeExtractedJsonShape(extractJsonObject(normalized));
        } catch (ModelAdapterException exception) {
            return "json-like-unextractable chars=" + normalized.length();
        }
    }

    private String describeExtractedJsonShape(String json) {
        if (json == null || json.isBlank()) {
            return "blank";
        }
        try {
            return describeJsonShape(objectMapper.readTree(json));
        } catch (IOException exception) {
            return "unreadable-json chars=" + json.length();
        }
    }

    private String describeJsonShape(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "null";
        }
        if (node.isArray()) {
            return "array(size=" + node.size() + ")";
        }
        if (!node.isObject()) {
            return node.getNodeType().name().toLowerCase();
        }
        JsonNode container = firstExisting(node, OUTLINE_CONTAINER_FIELDS);
        return "object(fields=%d,titleAlias=%s,sectionAlias=%s,missingAlias=%s,containerAlias=%s,containerType=%s)".formatted(
                objectFieldCount(node),
                firstExisting(node, TITLE_FIELDS) != null,
                firstExisting(node, SECTION_FIELDS) != null,
                firstExisting(node, MISSING_FIELDS) != null,
                container != null,
                container == null ? "none" : container.getNodeType().name().toLowerCase()
        );
    }

    private String describeSectionCandidateShape(JsonNode node) {
        if (node == null || !node.isObject()) {
            return "none";
        }
        JsonNode candidate = firstExisting(node, SECTION_FIELDS);
        if (candidate == null) {
            return "none";
        }
        if (candidate.isTextual()) {
            return "text(chars=" + candidate.asText().length() + ")";
        }
        if (candidate.isArray()) {
            return "array(size=" + candidate.size() + ")";
        }
        if (candidate.isObject()) {
            return "object(fields=" + objectFieldCount(candidate) + ")";
        }
        return candidate.getNodeType().name().toLowerCase();
    }

    private int objectFieldCount(JsonNode node) {
        if (node == null || !node.isObject()) {
            return 0;
        }
        int count = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            fields.next();
            count++;
        }
        return count;
    }

    private List<AiOutlineSection> parseOutlineSections(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        JsonNode candidate = firstExisting(root, SECTION_FIELDS);
        if (candidate != null) {
            List<AiOutlineSection> sections = parseSectionsNode(candidate);
            if (!sections.isEmpty()) {
                return sections;
            }
        }
        if (root.isArray() || looksLikeSection(root)) {
            return parseSectionsNode(root);
        }
        return List.of();
    }

    private List<AiOutlineSection> parseSectionsNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isTextual()) {
            return parseSingleTextOutline(node.asText());
        }
        if (node.isArray()) {
            List<AiOutlineSection> sections = new ArrayList<>();
            int index = 0;
            for (JsonNode item : node) {
                AiOutlineSection section = parseSectionNode(item, index);
                if (section != null && !section.heading().isBlank()) {
                    sections.add(section);
                    index++;
                }
            }
            return sections;
        }
        if (!node.isObject()) {
            return List.of();
        }

        JsonNode container = firstExisting(node, SECTION_CONTAINER_FIELDS);
        if (container != null && container != node) {
            List<AiOutlineSection> containerSections = parseSectionsNode(container);
            if (!containerSections.isEmpty()) {
                return containerSections;
            }
        }
        if (looksLikeSection(node)) {
            AiOutlineSection section = parseSectionNode(node, 0);
            return section == null || section.heading().isBlank() ? List.of() : List.of(section);
        }
        JsonNode nested = firstExisting(node, SECTION_FIELDS);
        if (nested != null && nested != node) {
            List<AiOutlineSection> nestedSections = parseSectionsNode(nested);
            if (!nestedSections.isEmpty()) {
                return nestedSections;
            }
        }

        List<AiOutlineSection> sections = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        int index = 0;
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (isMetadataField(field.getKey())) {
                continue;
            }
            AiOutlineSection section = parseMappedSection(field.getKey(), field.getValue(), index);
            if (section != null && !section.heading().isBlank()) {
                sections.add(section);
                index++;
            }
        }
        return sections;
    }

    private AiOutlineSection parseSectionNode(JsonNode node, int index) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            List<AiOutlineSection> parsed = parseOutlineText(node.asText());
            if (!parsed.isEmpty()) {
                return parsed.getFirst();
            }
            String heading = node.asText();
            return new AiOutlineSection(heading, List.of(), inferOutlineLevel(heading), List.of());
        }
        if (!node.isObject()) {
            return null;
        }
        String heading = firstText(node, HEADING_FIELDS);
        int level = parseOutlineLevel(firstExisting(node, LEVEL_FIELDS), heading);
        List<String> sourceRefs = parseTextList(firstExisting(node, SOURCE_REF_FIELDS));
        List<String> points = parseTextList(firstExisting(node, POINT_FIELDS));
        if (heading.isBlank() && !points.isEmpty()) {
            heading = sectionFallbackHeading(index);
            return new AiOutlineSection(heading, points, parseOutlineLevel(firstExisting(node, LEVEL_FIELDS), heading), sourceRefs);
        }
        if (heading.isBlank()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isMetadataField(field.getKey())) {
                    continue;
                }
                AiOutlineSection mapped = parseMappedSection(field.getKey(), field.getValue(), index);
                if (mapped != null && !mapped.heading().isBlank()) {
                    return mapped;
                }
            }
        }
        return heading.isBlank() ? null : new AiOutlineSection(heading, points, level, sourceRefs);
    }

    private AiOutlineSection parseMappedSection(String heading, JsonNode value, int index) {
        String normalizedHeading = heading == null ? "" : heading.strip();
        if (value != null && value.isObject()) {
            String explicitHeading = firstText(value, HEADING_FIELDS);
            List<String> explicitPoints = parseTextList(firstExisting(value, POINT_FIELDS));
            int explicitLevel = parseOutlineLevel(firstExisting(value, LEVEL_FIELDS), firstNonBlank(explicitHeading, normalizedHeading));
            List<String> explicitSourceRefs = parseTextList(firstExisting(value, SOURCE_REF_FIELDS));
            if (!explicitHeading.isBlank() || !explicitPoints.isEmpty()) {
                return new AiOutlineSection(
                        firstNonBlank(explicitHeading, normalizedHeading, sectionFallbackHeading(index)),
                        explicitPoints,
                        explicitLevel,
                        explicitSourceRefs
                );
            }
            AiOutlineSection parsed = parseSectionNode(value, index);
            if (parsed != null) {
                String finalHeading = parsed.heading().isBlank() ? normalizedHeading : parsed.heading();
                return new AiOutlineSection(finalHeading, parsed.points(), parsed.level(), parsed.sourceRefs());
            }
        }
        List<String> points = parseTextList(value);
        if (normalizedHeading.isBlank() && !points.isEmpty()) {
            normalizedHeading = sectionFallbackHeading(index);
        }
        return normalizedHeading.isBlank()
                ? null
                : new AiOutlineSection(normalizedHeading, points, inferOutlineLevel(normalizedHeading), List.of());
    }

    private List<AiOutlineSection> parseOutlineText(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<AiOutlineSection> sections = new ArrayList<>();
        String currentHeading = "";
        List<String> currentPoints = new ArrayList<>();
        for (String rawLine : value.split("\\R+")) {
            String line = cleanOutlineLine(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if (isOutlineHeadingLine(line)) {
                if (!currentHeading.isBlank()) {
                    sections.add(new AiOutlineSection(currentHeading, currentPoints, inferOutlineLevel(currentHeading), List.of()));
                }
                int separatorIndex = firstColonIndex(line);
                currentHeading = separatorIndex > 0 ? line.substring(0, separatorIndex).strip() : line;
                currentPoints = new ArrayList<>();
                if (separatorIndex > 0 && separatorIndex + 1 < line.length()) {
                    currentPoints.addAll(splitTextItems(line.substring(separatorIndex + 1)));
                }
            } else if (!currentHeading.isBlank()) {
                currentPoints.add(cleanPoint(line));
            }
        }
        if (!currentHeading.isBlank()) {
            sections.add(new AiOutlineSection(currentHeading, currentPoints, inferOutlineLevel(currentHeading), List.of()));
        }
        return sections;
    }

    private List<AiOutlineSection> parseSingleTextOutline(String value) {
        List<AiOutlineSection> sections = parseOutlineText(value);
        if (!sections.isEmpty()) {
            return sections;
        }
        String normalized = value == null ? "" : value.strip();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> points = splitTextItems(normalized);
        if (points.size() > 1) {
            return List.of(new AiOutlineSection(sectionFallbackHeading(0), points, 1, List.of()));
        }
        return List.of();
    }

    private int parseOutlineLevel(JsonNode levelNode, String heading) {
        int level = 0;
        if (levelNode != null && !levelNode.isMissingNode() && !levelNode.isNull()) {
            if (levelNode.isInt() || levelNode.isLong()) {
                level = levelNode.asInt();
            } else {
                String text = levelNode.asText("");
                if (!text.isBlank()) {
                    String compact = text.replaceAll("\\s+", "");
                    if (compact.matches(".*[1一壹].*")) {
                        level = 1;
                    } else if (compact.matches(".*[2二贰两].*")) {
                        level = 2;
                    } else if (compact.matches(".*[3三叁].*")) {
                        level = 3;
                    }
                }
            }
        }
        return level >= 1 && level <= 3 ? level : inferOutlineLevel(heading);
    }

    private int inferOutlineLevel(String heading) {
        String normalized = heading == null ? "" : heading.strip();
        if (normalized.matches("^[一二三四五六七八九十]+[、.．].+")
                || normalized.matches("^第[一二三四五六七八九十\\d]+[章节部分].+")) {
            return 1;
        }
        if (normalized.matches("^[（(][一二三四五六七八九十]+[）)].+")) {
            return 2;
        }
        if (normalized.matches("^\\d+[、.．)）].+")) {
            return 3;
        }
        return 0;
    }

    private boolean isOutlineHeadingLine(String line) {
        return line.matches("^[一二三四五六七八九十]+[、.．].+")
                || line.matches("^\\d+[、.．].+")
                || line.matches("^[（(][一二三四五六七八九十\\d]+[）)].+")
                || line.matches("^第[一二三四五六七八九十\\d]+[章节部分].+");
    }

    private String cleanOutlineLine(String value) {
        if (value == null) {
            return "";
        }
        return value.strip()
                .replaceFirst("^#{1,6}\\s*", "")
                .replaceFirst("^[-*•]\\s*", "")
                .strip();
    }

    private boolean looksLikeSection(JsonNode node) {
        return node != null
                && node.isObject()
                && (!firstText(node, HEADING_FIELDS).isBlank() || firstExisting(node, POINT_FIELDS) != null);
    }

    private List<String> parseTextList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return splitTextItems(node.asText());
        }
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                values.addAll(parseTextList(item));
            }
            return values;
        }
        if (node.isObject()) {
            JsonNode nested = firstExisting(node, POINT_FIELDS);
            if (nested != null && nested != node) {
                List<String> nestedValues = parseTextList(nested);
                if (!nestedValues.isEmpty()) {
                    return nestedValues;
                }
            }
            String text = firstText(node, TEXT_VALUE_FIELDS);
            if (!text.isBlank()) {
                return splitTextItems(text);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!isMetadataField(field.getKey())) {
                    values.addAll(parseTextList(field.getValue()));
                }
            }
        }
        return values.stream().map(String::strip).filter(value -> !value.isBlank()).toList();
    }

    private List<String> splitTextItems(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String normalized = value.strip();
        String[] parts = normalized.contains("\n") || normalized.contains("\r")
                ? normalized.split("\\R+")
                : normalized.split("[；;]");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String cleaned = cleanPoint(part);
            if (!cleaned.isBlank()) {
                values.add(cleaned);
            }
        }
        return values;
    }

    private String cleanPoint(String value) {
        if (value == null) {
            return "";
        }
        return value.strip()
                .replaceFirst("^[-*•\\s]+", "")
                .replaceFirst("^\\d+[、.．)）]\\s*", "")
                .strip();
    }

    private JsonNode firstExisting(JsonNode node, String[] fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            for (String field : fields) {
                if (entry.getKey().equalsIgnoreCase(field) && !entry.getValue().isNull()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String[] fields) {
        JsonNode value = firstExisting(node, fields);
        String text = textValue(value);
        return text == null ? "" : text.strip();
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray() && !node.isEmpty()) {
            return textValue(node.get(0));
        }
        if (node.isObject()) {
            JsonNode value = firstExisting(node, TEXT_VALUE_FIELDS);
            return value == node ? "" : textValue(value);
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String defaultTitleFor(String documentTypeCode) {
        return switch (documentTypeCode == null ? "" : documentTypeCode) {
            case "REQUEST" -> "关于有关事项的请示";
            case "REPORT" -> "关于有关事项的报告";
            default -> "关于有关事项的通知";
        };
    }

    private String sectionFallbackHeading(int index) {
        return "第 " + (index + 1) + " 部分";
    }

    private boolean isMetadataField(String field) {
        return field == null
                || matchesAny(field, TITLE_FIELDS)
                || matchesAny(field, MISSING_FIELDS)
                || matchesAny(field, LEVEL_FIELDS)
                || matchesAny(field, SOURCE_REF_FIELDS)
                || field.equalsIgnoreCase("traceId")
                || field.equalsIgnoreCase("id")
                || field.equalsIgnoreCase("createdAt")
                || field.equalsIgnoreCase("updatedAt");
    }

    private boolean matchesAny(String value, String[] candidates) {
        for (String candidate : candidates) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private int firstColonIndex(String value) {
        int chinese = value.indexOf('：');
        int english = value.indexOf(':');
        if (chinese < 0) {
            return english;
        }
        if (english < 0) {
            return chinese;
        }
        return Math.min(chinese, english);
    }

    private String streamDeltaFromLine(String line) throws IOException {
        String normalized = line == null ? "" : line.strip();
        if (normalized.isBlank() || !normalized.startsWith("data:")) {
            return null;
        }
        String data = normalized.substring("data:".length()).strip();
        if (data.isBlank() || "[DONE]".equals(data)) {
            return null;
        }
        DeepSeekStreamChunk chunk = objectMapper.readValue(data, DeepSeekStreamChunk.class);
        return chunk.firstDeltaContent();
    }

    static String extractJsonObject(String content) {
        String normalized = content == null ? "" : stripMarkdownFence(content.strip());
        if (normalized.isBlank()) {
            throw new ModelAdapterException("AI_DEEPSEEK_EMPTY_RESPONSE", "DeepSeek 返回内容为空");
        }
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            return normalized;
        }
        int start = normalized.indexOf('{');
        if (start < 0) {
            throw new ModelAdapterException("AI_DEEPSEEK_RESPONSE_INVALID", "DeepSeek 返回结构无效");
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = inString;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return normalized.substring(start, i + 1);
                }
            }
        }
        throw new ModelAdapterException("AI_DEEPSEEK_RESPONSE_INVALID", "DeepSeek 返回结构无效");
    }

    private static String stripMarkdownFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineEnd = content.indexOf('\n');
        if (firstLineEnd < 0) {
            return content;
        }
        int closingFence = content.lastIndexOf("```");
        if (closingFence <= firstLineEnd) {
            return content.substring(firstLineEnd + 1).strip();
        }
        return content.substring(firstLineEnd + 1, closingFence).strip();
    }

    private String systemPrompt(String task) {
        return task + "必须只返回合法 JSON，不要输出 Markdown。不要编造材料中不存在的事实。";
    }

    private String outlineUserPrompt(OutlinePrompt prompt) {
        return """
                请基于以下信息生成可直接套版的全文公文正文结构，返回 JSON：
                {
                  "titleSuggestion": "标题建议",
                  "sections": [
                    {"level": 1, "heading": "一、...", "points": ["要点"], "sourceRefs": ["materialId=1: 文件名"]},
                    {"level": 2, "heading": "（一）...", "points": ["要点"], "sourceRefs": []},
                    {"level": 3, "heading": "1. ...", "points": ["要点"], "sourceRefs": []}
                  ],
                  "missingInformation": ["缺失信息"]
                }
                要求：
                1. sections 必须覆盖全文正文结构，不能只返回一级标题；按公文写作需要给出一级、二级、三级标题。
                2. sections 使用扁平有序数组，不要嵌套 children；level 只允许 1、2、3。
                3. AI 只生成标题结构和写作要点，不要输出字体、字号、缩进、行距等格式指令。
                4. 可以参考材料摘要；sourceRefs 只写材料 id、文件名或摘要编号，不要复制材料全文。
                5. 不要编造材料中不存在的事实；信息不足时写入 missingInformation。
                6. 即使字段摘要、材料摘要或补充要求为空，也必须按文种返回不少于 3 个一级标题和必要的二级标题，sections 绝不能是空数组。
                文种：%s
                标题：%s
                字段摘要：%s
                材料摘要：%s
                选中节点：%s
                补充要求：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.title(),
                prompt.fieldSummaries(),
                prompt.materialSummaries(),
                prompt.nodeContext().promptSummary(),
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
                目标节点：%s
                格式与套版约束：%s
                补充要求：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.title(),
                prompt.heading(),
                prompt.points(),
                prompt.fieldSummaries(),
                prompt.materialSummaries(),
                prompt.nodeContext().promptSummary(),
                prompt.formattingSummary(),
                prompt.instruction()
        );
    }

    private String paragraphCandidateUserPrompt(ParagraphPrompt prompt) {
        return """
                请为“段落标题”对应的正文节点生成候选正文，返回 JSON：
                {"content":"正文节点内容"}
                输出规则：
                1. 工作台已经单独保留段落标题节点；content 只写标题下的正文内容，不要只返回标题，不要重复输出段落标题。
                2. content 至少包含一个完整句子，应围绕段落要点展开，不能只写空泛短语。
                3. 必须结合文种、字段摘要、材料摘要、目标节点和格式约束；语气严肃克制，符合公文表达。
                4. 事实只能来自字段摘要、材料摘要和补充要求；信息不足时用“需进一步补充”“拟结合实际完善”等审慎表述，不要编造具体数据、日期、人员或结论。
                5. 不要输出 Markdown、编号列表、解释说明或代码块。
                文种：%s
                草稿标题：%s
                段落标题：%s
                段落要点：%s
                字段摘要：%s
                材料摘要：%s
                目标节点：%s
                格式与套版约束：%s
                补充要求：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.title(),
                prompt.heading(),
                prompt.points(),
                prompt.fieldSummaries(),
                prompt.materialSummaries(),
                prompt.nodeContext().promptSummary(),
                prompt.formattingSummary(),
                prompt.instruction()
        );
    }

    private String paragraphCandidateStreamUserPrompt(ParagraphPrompt prompt) {
        return """
                请为“段落标题”对应的正文节点生成候选正文。
                输出要求：
                1. 只输出正文节点纯文本，不要输出 JSON、Markdown、代码块或解释。
                2. 工作台已经单独保留段落标题节点；不要只返回标题，不要重复输出段落标题。
                3. 只生成这一节标题下的正文内容，不要生成其他提纲章节。
                4. 正文至少包含一个完整句子，应围绕段落要点展开，不能只写空泛短语。
                5. 必须结合文种、字段摘要、材料摘要、目标节点和格式约束；语气严肃克制，符合公文表达。
                6. 事实只能来自字段摘要、材料摘要和补充要求；信息不足时用“需进一步补充”“拟结合实际完善”等审慎表述，不要编造具体数据、日期、人员或结论。
                文种：%s
                草稿标题：%s
                段落标题：%s
                段落要点：%s
                字段摘要：%s
                材料摘要：%s
                目标节点：%s
                格式与套版约束：%s
                补充要求：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.title(),
                prompt.heading(),
                prompt.points(),
                prompt.fieldSummaries(),
                prompt.materialSummaries(),
                prompt.nodeContext().promptSummary(),
                prompt.formattingSummary(),
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
                目标节点：nodeId=%s;role=%s;title=%s
                原段落：%s
                字段摘要：%s
                材料摘要：%s
                补充要求：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.title(),
                prompt.operationType(),
                prompt.targetNodeId() == null ? "" : prompt.targetNodeId(),
                prompt.targetNodeRole(),
                prompt.targetNodeTitle(),
                prompt.originalText(),
                prompt.fieldSummaries(),
                prompt.materialSummaries(),
                prompt.instruction()
        );
    }

    private String qualityReviewUserPrompt(QualityCheckPrompt prompt) {
        return """
                请审阅以下中文公文草稿，返回 JSON：
                {
                  "suggestions": [
                    {
                      "severity": "INFO|WARNING|ERROR",
                      "category": "AI_EXPRESSION|AI_STRUCTURE|AI_RISK|AI_MATERIAL",
                      "code": "稳定的大写英文代码",
                      "message": "面向起草人的问题描述",
                      "suggestion": "具体修改建议"
                    }
                  ]
                }
                要求：
                1. 不要重复规则检查已发现的问题，重点看表达、语气、结构衔接、事实风险和公文规范性。
                2. 不要直接重写全文，不要输出 Markdown。
                3. 不要编造材料中不存在的事实；事实不足时给出补充材料建议。
                4. 最多返回 5 条建议，severity 只允许 INFO、WARNING、ERROR。
                文种：%s
                标题：%s
                字段摘要：%s
                正文摘要：%s
                材料摘要：%s
                已有规则检查：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.title(),
                prompt.fieldSummaries(),
                prompt.bodySummaries(),
                prompt.materialSummaries(),
                prompt.ruleSummaries()
        );
    }

    private String templateAnalysisUserPrompt(TemplateAnalysisPrompt prompt) {
        return """
                请判断一个没有显式占位符的 Word 文件是否适合作为公文套版模板，返回 JSON：
                {
                  "templateKind": "STANDARD_PLACEHOLDER_TEMPLATE|STYLE_TEMPLATE|REFERENCE_DOCUMENT|ORDINARY_DOCUMENT|UNKNOWN_DOCUMENT",
                  "confidence": 0.0,
                  "documentTypeCode": "NOTICE|REQUEST|REPORT|UNKNOWN",
                  "inferredFields": ["标题","主送","正文","落款","日期"],
                  "suggestedPlaceholders": [{"field":"标题","reason":"判断依据"}],
                  "message": "面向模板管理员的简短说明"
                }
                要求：
                1. 当前文件已经确认没有 {{字段名}} 占位符，不要声称它有占位符。
                2. 如果像完整公文范文，templateKind 用 REFERENCE_DOCUMENT。
                3. 如果像空白格式或样式模板，templateKind 用 STYLE_TEMPLATE。
                4. 不要编造正文之外的信息，最多建议 8 个占位符。
                目标文种：%s
                文件名：%s
                样式：%s
                表格数量：%s
                有页眉：%s
                有页脚：%s
                文本摘要：%s
                """.formatted(
                prompt.documentTypeCode(),
                prompt.originalFileName(),
                prompt.styleNames(),
                prompt.tableCount(),
                prompt.hasHeader(),
                prompt.hasFooter(),
                prompt.textSample()
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
    private record DeepSeekStreamChunk(List<DeepSeekStreamChoice> choices) {
        private String firstDeltaContent() {
            if (choices == null || choices.isEmpty()) {
                return "";
            }
            DeepSeekStreamDelta delta = choices.getFirst().delta();
            return delta == null || delta.content() == null ? "" : delta.content();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekStreamChoice(DeepSeekStreamDelta delta) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekStreamDelta(String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekOutlinePayload(
            @JsonAlias({"title", "title_suggestion", "标题", "标题建议"}) String titleSuggestion,
            @JsonAlias({"outline", "items", "提纲", "章节"})
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<DeepSeekOutlineSectionPayload> sections,
            @JsonAlias({"missing", "missing_information", "缺失信息"})
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<String> missingInformation
    ) {
        private DeepSeekOutlinePayload {
            sections = sections == null ? List.of() : sections;
            missingInformation = missingInformation == null ? List.of() : missingInformation;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekOutlineSectionPayload(
            @JsonAlias({"title", "headingText", "sectionTitle", "标题", "小标题"}) String heading,
            @JsonAlias({"items", "keyPoints", "children", "要点"})
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<String> points
    ) {
        private DeepSeekOutlineSectionPayload {
            points = points == null ? List.of() : points;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekParagraphPayload(
            @JsonAlias({"text", "paragraph", "body", "正文"}) String content
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekLocalOperationPayload(
            @JsonAlias({"content", "suggestion", "text", "result", "修改建议"}) String suggestionText
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekQualityPayload(
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<DeepSeekQualitySuggestionPayload> suggestions
    ) {
        private DeepSeekQualityPayload {
            suggestions = suggestions == null ? List.of() : suggestions;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekQualitySuggestionPayload(
            String severity,
            String category,
            String code,
            String message,
            String suggestion
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekTemplateAnalysisPayload(
            String templateKind,
            double confidence,
            String documentTypeCode,
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<String> inferredFields,
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<DeepSeekPlaceholderSuggestionPayload> suggestedPlaceholders,
            String message
    ) {
        private DeepSeekTemplateAnalysisPayload {
            inferredFields = inferredFields == null ? List.of() : inferredFields;
            suggestedPlaceholders = suggestedPlaceholders == null ? List.of() : suggestedPlaceholders;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekPlaceholderSuggestionPayload(String field, String reason) {
    }
}
