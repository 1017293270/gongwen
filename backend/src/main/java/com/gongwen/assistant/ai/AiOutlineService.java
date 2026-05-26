package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.material.MaterialRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AiOutlineService {
    private static final int MAX_INSTRUCTION_LENGTH = 1000;

    private final DraftService draftService;
    private final MaterialRepository materialRepository;
    private final PromptBuilder promptBuilder;
    private final ModelAdapter modelAdapter;
    private final AiGenerationTraceRepository traceRepository;

    public AiOutlineService(
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

    public AiOutlineResponse generateOutline(long draftId, AiOutlineRequest request) {
        String instruction = request == null ? "" : request.instruction();
        if (instruction != null && instruction.length() > MAX_INSTRUCTION_LENGTH) {
            throw new AiOutlineException("AI_OUTLINE_INSTRUCTION_TOO_LONG", "补充要求不能超过 1000 字");
        }

        DraftDetailDto draft = draftService.getDraft(draftId);
        List<MaterialPromptSummary> materials = materialRepository.findReadyTextSummariesByDraftId(draftId);
        OutlinePrompt prompt = promptBuilder.buildOutlinePrompt(draft, materials, instruction);
        UUID traceId = UUID.randomUUID();
        Instant startedAt = Instant.now();

        try {
            AiOutlineResponse adapterResponse = modelAdapter.generateOutline(prompt);
            AiOutlineResponse response = new AiOutlineResponse(
                    traceId,
                    adapterResponse.titleSuggestion(),
                    adapterResponse.sections(),
                    adapterResponse.missingInformation()
            );
            validate(response);
            traceRepository.save(successTrace(traceId, draftId, prompt, response, startedAt));
            return response;
        } catch (ModelAdapterException exception) {
            traceRepository.save(failedTrace(traceId, draftId, prompt, exception.errorCode(), exception.getMessage(), startedAt));
            throw new AiOutlineException("AI_MODEL_UNAVAILABLE", "AI 服务暂不可用，请稍后重试");
        } catch (IllegalArgumentException exception) {
            traceRepository.save(failedTrace(traceId, draftId, prompt, "AI_RESPONSE_INVALID", exception.getMessage(), startedAt));
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
                "title=%s;sections=%d;missing=%d".formatted(
                        response.titleSuggestion(),
                        response.sections().size(),
                        response.missingInformation().size()
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
}
