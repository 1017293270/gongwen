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
            AiOutlineResponse response = new AiOutlineResponse(
                    traceId,
                    adapterResponse.titleSuggestion(),
                    adapterResponse.sections(),
                    adapterResponse.missingInformation(),
                    nodeSuggestions(adapterResponse, nodeContext)
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
                "title=%s;sections=%d;missing=%d;nodeSuggestions=%d".formatted(
                        response.titleSuggestion(),
                        response.sections().size(),
                        response.missingInformation().size(),
                        response.nodeSuggestions().size()
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

    private List<AiNodeSuggestion> nodeSuggestions(AiOutlineResponse response, AiNodeContext nodeContext) {
        if (nodeContext.present() && nodeContext.nodeId() != null) {
            return List.of(new AiNodeSuggestion(
                    nodeContext.nodeId(),
                    nodeContext.nodeRole().isBlank() ? "BODY" : nodeContext.nodeRole(),
                    nodeContext.nodeTitle(),
                    "UPDATE",
                    summarizeSections(response.sections())
            ));
        }
        return response.sections().stream()
                .map(section -> new AiNodeSuggestion(
                        null,
                        "BODY",
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
