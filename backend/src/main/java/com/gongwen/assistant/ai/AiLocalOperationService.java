package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.material.MaterialRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AiLocalOperationService {
    private static final int MAX_INSTRUCTION_LENGTH = 1000;

    private final DraftService draftService;
    private final MaterialRepository materialRepository;
    private final PromptBuilder promptBuilder;
    private final ModelAdapter modelAdapter;
    private final AiGenerationTraceRepository traceRepository;

    public AiLocalOperationService(
            DraftService draftService,
            MaterialRepository materialRepository,
            PromptBuilder promptBuilder,
            ModelAdapter modelAdapter,
            AiGenerationTraceRepository traceRepository
    ) {
        this.draftService = draftService;
        this.materialRepository = materialRepository;
        this.promptBuilder = promptBuilder;
        this.modelAdapter = modelAdapter;
        this.traceRepository = traceRepository;
    }

    public AiLocalOperationResponse generateSuggestion(long draftId, AiLocalOperationRequest request) {
        validateRequest(request);
        DraftDetailDto draft = draftService.getDraft(draftId);
        DraftBlockDto targetBlock = findTargetBlock(draft, request.targetBlockId());
        List<MaterialPromptSummary> materials = materialRepository.findReadyTextSummariesByDraftId(draftId);
        LocalOperationPrompt prompt = promptBuilder.buildLocalOperationPrompt(draft, materials, targetBlock, request);
        UUID traceId = UUID.randomUUID();
        Instant startedAt = Instant.now();

        try {
            AiLocalOperationModelResponse modelResponse = modelAdapter.generateLocalOperation(prompt);
            if (modelResponse.suggestionText() == null || modelResponse.suggestionText().isBlank()) {
                throw new IllegalArgumentException("suggestionText is required");
            }
            String suggestion = modelResponse.suggestionText().strip();
            traceRepository.save(successTrace(traceId, draftId, prompt, suggestion, startedAt));
            return new AiLocalOperationResponse(traceId, targetBlock.id(), request.operationType(), suggestion);
        } catch (ModelAdapterException exception) {
            traceRepository.save(failedTrace(traceId, draftId, prompt, exception.errorCode(), exception.getMessage(), startedAt));
            throw new AiOutlineException("AI_MODEL_UNAVAILABLE", "AI 服务暂不可用，请稍后重试");
        } catch (IllegalArgumentException exception) {
            traceRepository.save(failedTrace(traceId, draftId, prompt, "AI_RESPONSE_INVALID", exception.getMessage(), startedAt));
            throw new AiOutlineException("AI_RESPONSE_INVALID", "AI 返回局部建议结构无效，请重试");
        }
    }

    private void validateRequest(AiLocalOperationRequest request) {
        if (request == null || request.targetBlockId() == null) {
            throw new AiOutlineException("AI_LOCAL_TARGET_REQUIRED", "请选择要处理的正文段落");
        }
        if (request.operationType() == null) {
            throw new AiOutlineException("AI_LOCAL_OPERATION_REQUIRED", "请选择局部操作类型");
        }
        if (request.instruction() != null && request.instruction().length() > MAX_INSTRUCTION_LENGTH) {
            throw new AiOutlineException("AI_LOCAL_INSTRUCTION_TOO_LONG", "补充要求不能超过 1000 字");
        }
    }

    private DraftBlockDto findTargetBlock(DraftDetailDto draft, long targetBlockId) {
        DraftBlockDto block = draft.blocks().stream()
                .filter(candidate -> candidate.id() == targetBlockId)
                .findFirst()
                .orElseThrow(() -> new AiOutlineException("AI_LOCAL_TARGET_NOT_FOUND", "目标段落不存在"));
        if (!"BODY_PARAGRAPH".equals(block.blockType())) {
            throw new AiOutlineException("AI_LOCAL_TARGET_NOT_BODY", "只能对正文段落执行局部 AI 操作");
        }
        if (block.content() == null || block.content().isBlank()) {
            throw new AiOutlineException("AI_LOCAL_TARGET_EMPTY", "目标正文段落为空");
        }
        return block;
    }

    private AiGenerationTrace successTrace(
            UUID traceId,
            long draftId,
            LocalOperationPrompt prompt,
            String suggestion,
            Instant startedAt
    ) {
        return new AiGenerationTrace(
                traceId,
                draftId,
                "LOCAL_OPERATION",
                modelAdapter.provider(),
                modelAdapter.modelName(),
                "SUCCESS",
                prompt.promptVersion(),
                prompt.inputSummary(),
                "targetBlockId=%d;operationType=%s;suggestionChars=%d".formatted(
                        prompt.targetBlockId(),
                        prompt.operationType(),
                        suggestion.length()
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
            LocalOperationPrompt prompt,
            String errorCode,
            String errorMessage,
            Instant startedAt
    ) {
        return new AiGenerationTrace(
                traceId,
                draftId,
                "LOCAL_OPERATION",
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
}
