package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
import com.gongwen.assistant.material.MaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AiOutlineService {
    private static final Logger log = LoggerFactory.getLogger(AiOutlineService.class);
    private static final int MAX_INSTRUCTION_LENGTH = 1000;

    private final DraftService draftService;
    private final MaterialRepository materialRepository;
    private final PromptBuilder promptBuilder;
    private final ModelAdapter modelAdapter;
    private final AiGenerationTraceRepository traceRepository;
    private final DraftNodeRepository draftNodeRepository;

    public AiOutlineService(
            DraftService draftService,
            MaterialRepository materialRepository,
            PromptBuilder promptBuilder,
            ModelAdapter modelAdapter,
            AiGenerationTraceRepository traceRepository,
            DraftNodeRepository draftNodeRepository
    ) {
        this.draftService = draftService;
        this.materialRepository = materialRepository;
        this.promptBuilder = promptBuilder;
        this.modelAdapter = modelAdapter;
        this.traceRepository = traceRepository;
        this.draftNodeRepository = draftNodeRepository;
    }

    public AiOutlineResponse generateOutline(long draftId, AiOutlineRequest request) {
        String instruction = request == null ? "" : request.instruction();
        if (instruction != null && instruction.length() > MAX_INSTRUCTION_LENGTH) {
            throw new AiOutlineException("AI_OUTLINE_INSTRUCTION_TOO_LONG", "补充要求不能超过 1000 字");
        }

        DraftDetailDto draft = draftService.getDraft(draftId);
        AiNodeContext nodeContext = resolveNodeContext(draftId, request);
        List<MaterialPromptSummary> materials = materialRepository.findReadyTextSummariesByDraftId(draftId);
        OutlinePrompt prompt = promptBuilder.buildOutlinePrompt(draft, materials, instruction, nodeContext);
        UUID traceId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        log.info(
                "AI outline generation started draftId={} traceId={} provider={} model={} inputSummary={}",
                draftId,
                traceId,
                modelAdapter.provider(),
                modelAdapter.modelName(),
                prompt.inputSummary()
        );

        try {
            AiOutlineResponse adapterResponse = modelAdapter.generateOutline(prompt);
            List<AiOutlineSection> normalizedSections = normalizeSections(adapterResponse.sections());
            List<String> missingInformation = adapterResponse.missingInformation();
            if (normalizedSections.isEmpty()) {
                normalizedSections = fallbackSections(prompt);
                missingInformation = withFallbackMissingInformation(missingInformation);
                log.warn(
                        "AI outline response contained no sections; using fallback outline draftId={} traceId={} provider={} model={} inputSummary={}",
                        draftId,
                        traceId,
                        modelAdapter.provider(),
                        modelAdapter.modelName(),
                        prompt.inputSummary()
                );
            }
            AiOutlineResponse response = new AiOutlineResponse(
                    traceId,
                    firstNonBlank(adapterResponse.titleSuggestion(), draft.title(), defaultTitle(prompt.documentTypeCode())),
                    normalizedSections,
                    missingInformation,
                    nodeSuggestions(normalizedSections, nodeContext)
            );
            validate(response);
            traceRepository.save(successTrace(traceId, draftId, prompt, response, startedAt));
            log.info(
                    "AI outline generation succeeded draftId={} traceId={} sections={} missing={} durationMs={}",
                    draftId,
                    traceId,
                    response.sections().size(),
                    response.missingInformation().size(),
                    Duration.between(startedAt, Instant.now()).toMillis()
            );
            return response;
        } catch (ModelAdapterException exception) {
            traceRepository.save(failedTrace(traceId, draftId, prompt, exception.errorCode(), exception.getMessage(), startedAt));
            String responseErrorCode = responseErrorCode(exception);
            log.warn(
                    "AI outline generation failed draftId={} traceId={} adapterErrorCode={} responseErrorCode={} durationMs={} inputSummary={}",
                    draftId,
                    traceId,
                    exception.errorCode(),
                    responseErrorCode,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    prompt.inputSummary()
            );
            throw new AiOutlineException(responseErrorCode, responseErrorMessage(responseErrorCode, exception));
        } catch (IllegalArgumentException exception) {
            traceRepository.save(failedTrace(traceId, draftId, prompt, "AI_RESPONSE_INVALID", exception.getMessage(), startedAt));
            log.warn(
                    "AI outline response validation failed draftId={} traceId={} reason={} durationMs={} inputSummary={}",
                    draftId,
                    traceId,
                    exception.getMessage(),
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    prompt.inputSummary()
            );
            throw new AiOutlineException("AI_RESPONSE_INVALID", "AI 返回结构无效，请重试");
        }
    }

    private void validate(AiOutlineResponse response) {
        if (response.titleSuggestion() == null || response.titleSuggestion().isBlank()) {
            throw new IllegalArgumentException("titleSuggestion is required");
        }
        if (response.sections() == null || response.sections().isEmpty()) {
            throw new IllegalArgumentException("sections are required");
        }
        if (response.sections().stream().noneMatch(section -> section.level() == 1)) {
            throw new IllegalArgumentException("at least one level 1 section is required");
        }
    }

    private List<AiOutlineSection> fallbackSections(OutlinePrompt prompt) {
        String documentTypeCode = prompt == null ? "" : prompt.documentTypeCode();
        return switch (documentTypeCode == null ? "" : documentTypeCode.strip().toUpperCase()) {
            case "REPORT" -> List.of(
                    new AiOutlineSection("一、基本情况", List.of("概述工作背景、总体进展和主要成效"), 1, List.of()),
                    new AiOutlineSection("（一）工作开展情况", List.of("梳理重点任务推进情况"), 2, List.of()),
                    new AiOutlineSection("二、存在问题", List.of("归纳当前不足、原因和风险点"), 1, List.of()),
                    new AiOutlineSection("（一）主要问题", List.of("列明需要进一步核实和补充的问题"), 2, List.of()),
                    new AiOutlineSection("三、下一步安排", List.of("提出改进措施、责任分工和时间要求"), 1, List.of()),
                    new AiOutlineSection("（一）工作措施", List.of("明确具体推进路径和保障要求"), 2, List.of())
            );
            case "REQUEST" -> List.of(
                    new AiOutlineSection("一、请示事项", List.of("说明拟请示的具体事项和目标"), 1, List.of()),
                    new AiOutlineSection("（一）事项背景", List.of("概述事项来源和现实需要"), 2, List.of()),
                    new AiOutlineSection("二、主要依据", List.of("梳理政策依据、工作依据和必要性"), 1, List.of()),
                    new AiOutlineSection("（一）依据说明", List.of("列明需要补充的文件、数据或事实依据"), 2, List.of()),
                    new AiOutlineSection("三、拟办建议", List.of("提出办理方案、资源需求和请示结论"), 1, List.of()),
                    new AiOutlineSection("（一）实施安排", List.of("明确责任、步骤和时间节点"), 2, List.of())
            );
            default -> List.of(
                    new AiOutlineSection("一、背景与依据", List.of("概述发文背景、工作依据和现实需要"), 1, List.of()),
                    new AiOutlineSection("（一）主要依据", List.of("梳理上级要求、政策依据和相关事实"), 2, List.of()),
                    new AiOutlineSection("二、主要事项", List.of("明确拟通知、部署或说明的重点事项"), 1, List.of()),
                    new AiOutlineSection("（一）重点任务", List.of("列明任务安排、责任分工和推进要求"), 2, List.of()),
                    new AiOutlineSection("三、工作要求", List.of("提出落实要求、报送要求和保障措施"), 1, List.of()),
                    new AiOutlineSection("（一）组织保障", List.of("明确组织领导、协同机制和时间节点"), 2, List.of())
            );
        };
    }

    private List<String> withFallbackMissingInformation(List<String> missingInformation) {
        List<String> values = new ArrayList<>(missingInformation == null ? List.of() : missingInformation);
        values.add("AI 未返回可用提纲结构，已按文种生成默认结构；请补充材料或要求后再调整。");
        return values;
    }

    private String defaultTitle(String documentTypeCode) {
        return switch (documentTypeCode == null ? "" : documentTypeCode.strip().toUpperCase()) {
            case "REPORT" -> "工作情况报告";
            case "REQUEST" -> "关于有关事项的请示";
            default -> "关于有关事项的通知";
        };
    }

    private AiGenerationTrace successTrace(
            UUID traceId,
            long draftId,
            OutlinePrompt prompt,
            AiOutlineResponse response,
            Instant startedAt
    ) {
        return new AiGenerationTrace(
                traceId,
                draftId,
                "OUTLINE",
                modelAdapter.provider(),
                modelAdapter.modelName(),
                "SUCCESS",
                prompt.promptVersion(),
                prompt.inputSummary(),
                "title=%s;sections=%d;missing=%d;nodeSuggestions=%d;sourceRefs=%d".formatted(
                        response.titleSuggestion(),
                        response.sections().size(),
                        response.missingInformation().size(),
                        response.nodeSuggestions().size(),
                        response.sections().stream().mapToInt(section -> section.sourceRefs().size()).sum()
                ),
                null,
                null,
                Duration.between(startedAt, Instant.now()).toMillis(),
                Instant.now()
        );
    }

    private AiGenerationTrace failedTrace(
            UUID traceId,
            long draftId,
            OutlinePrompt prompt,
            String errorCode,
            String errorMessage,
            Instant startedAt
    ) {
        return new AiGenerationTrace(
                traceId,
                draftId,
                "OUTLINE",
                modelAdapter.provider(),
                modelAdapter.modelName(),
                "FAILED",
                prompt.promptVersion(),
                prompt.inputSummary(),
                null,
                errorCode,
                errorMessage,
                Duration.between(startedAt, Instant.now()).toMillis(),
                Instant.now()
        );
    }

    private AiNodeContext resolveNodeContext(long draftId, AiOutlineRequest request) {
        if (request == null) {
            return AiNodeContext.none();
        }
        DraftNode node = null;
        if (request.nodeId() != null) {
            node = draftNodeRepository.findByDraftId(draftId).stream()
                    .filter(candidate -> candidate.id() == request.nodeId())
                    .findFirst()
                    .orElseThrow(() -> new AiOutlineException("AI_NODE_TARGET_NOT_FOUND", "目标结构节点不存在"));
        }
        return new AiNodeContext(
                request.nodeId(),
                firstNonBlank(node == null ? null : node.role(), request.nodeRole()),
                firstNonBlank(node == null ? null : node.title(), request.nodeTitle()),
                firstNonBlank(request.nodeContext(), node == null ? null : node.content())
        );
    }

    private List<AiOutlineSection> normalizeSections(List<AiOutlineSection> sections) {
        List<AiOutlineSection> normalizedSections = new ArrayList<>();
        int previousLevel = 0;
        int index = 0;
        for (AiOutlineSection section : sections == null ? List.<AiOutlineSection>of() : sections) {
            if (section == null) {
                continue;
            }
            String heading = section.heading() == null ? "" : section.heading().strip();
            if (heading.isBlank()) {
                throw new IllegalArgumentException("section heading is required");
            }
            int level = section.level() >= 1 && section.level() <= 3
                    ? section.level()
                    : inferSectionLevel(heading);
            if (level < 1 || level > 3) {
                throw new IllegalArgumentException("section level is invalid: " + section.level());
            }
            if (index == 0 && level != 1) {
                throw new IllegalArgumentException("first section must be level 1");
            }
            if (previousLevel > 0 && level > previousLevel + 1) {
                throw new IllegalArgumentException("section level jumps from " + previousLevel + " to " + level);
            }
            normalizedSections.add(new AiOutlineSection(heading, section.points(), level, section.sourceRefs()));
            previousLevel = level;
            index++;
        }
        return normalizedSections;
    }

    private int inferSectionLevel(String heading) {
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
        return 1;
    }

    private List<AiNodeSuggestion> nodeSuggestions(List<AiOutlineSection> sections, AiNodeContext nodeContext) {
        if (nodeContext.present() && nodeContext.nodeId() != null) {
            return List.of(new AiNodeSuggestion(
                    nodeContext.nodeId(),
                    nodeContext.nodeRole().isBlank() ? "BODY" : nodeContext.nodeRole(),
                    nodeContext.nodeTitle(),
                    "UPDATE",
                    summarizeSections(sections)
            ));
        }
        return sections.stream()
                .map(section -> new AiNodeSuggestion(
                        null,
                        "BODY_HEADING_LEVEL_" + section.level(),
                        section.heading(),
                        "CREATE",
                        String.join("；", section.points())
                ))
                .toList();
    }

    private String summarizeSections(List<AiOutlineSection> sections) {
        return sections.stream()
                .map(section -> section.heading() + "：" + String.join("；", section.points()))
                .findFirst()
                .orElse("");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        return second == null ? "" : second.strip();
    }

    private String firstNonBlank(String first, String second, String third) {
        String value = firstNonBlank(first, second);
        return value.isBlank() ? firstNonBlank(third, "") : value;
    }

    private String responseErrorCode(ModelAdapterException exception) {
        String errorCode = exception.errorCode() == null ? "" : exception.errorCode();
        if (errorCode.contains("RESPONSE_INVALID") || errorCode.contains("EMPTY_RESPONSE")) {
            return "AI_RESPONSE_INVALID";
        }
        return "AI_MODEL_UNAVAILABLE";
    }

    private String responseErrorMessage(String responseErrorCode, ModelAdapterException exception) {
        if ("AI_RESPONSE_INVALID".equals(responseErrorCode)) {
            if (exception.errorCode() != null && exception.errorCode().contains("EMPTY_RESPONSE")) {
                return "AI 返回内容为空，请重试";
            }
            return "AI 返回结构无效，请重试";
        }
        return "AI 服务暂不可用：" + exception.getMessage();
    }
}
