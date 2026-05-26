package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.draft.UpdateDraftBlocksRequest;
import com.gongwen.assistant.material.MaterialRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AiParagraphService {
    private static final int MAX_INSTRUCTION_LENGTH = 1000;

    private final DraftService draftService;
    private final MaterialRepository materialRepository;
    private final PromptBuilder promptBuilder;
    private final ModelAdapter modelAdapter;
    private final AiGenerationTraceRepository traceRepository;

    public AiParagraphService(
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

    public AiParagraphResponse generateParagraph(long draftId, AiParagraphRequest request) {
        validateRequest(request);
        DraftDetailDto draft = draftService.getDraft(draftId);
        List<MaterialPromptSummary> materials = materialRepository.findReadyTextSummariesByDraftId(draftId);
        ParagraphPrompt prompt = promptBuilder.buildParagraphPrompt(draft, materials, request);
        UUID traceId = UUID.randomUUID();
        Instant startedAt = Instant.now();

        try {
            AiParagraphModelResponse modelResponse = modelAdapter.generateParagraph(prompt);
            if (modelResponse.content() == null || modelResponse.content().isBlank()) {
                throw new IllegalArgumentException("content is required");
            }
            int sortOrder = request.sortOrder() == null || request.sortOrder() <= 0
                    ? nextBodySortOrder(draft)
                    : request.sortOrder();
            DraftDetailDto updatedDraft = draftService.updateBlocks(
                    draftId,
                    new UpdateDraftBlocksRequest(upsertParagraphBlock(draft, modelResponse.content().strip(), sortOrder))
            );
            DraftBlockDto generatedBlock = updatedDraft.blocks().stream()
                    .filter(block -> "BODY_PARAGRAPH".equals(block.blockType()) && block.sortOrder() == sortOrder)
                    .findFirst()
                    .orElseThrow();
            traceRepository.save(successTrace(traceId, draftId, prompt, generatedBlock, startedAt));
            return new AiParagraphResponse(traceId, updatedDraft, generatedBlock);
        } catch (ModelAdapterException exception) {
            traceRepository.save(failedTrace(traceId, draftId, prompt, exception.errorCode(), exception.getMessage(), startedAt));
            throw new AiOutlineException("AI_MODEL_UNAVAILABLE", "AI 服务暂不可用，请稍后重试");
        } catch (IllegalArgumentException exception) {
            traceRepository.save(failedTrace(traceId, draftId, prompt, "AI_RESPONSE_INVALID", exception.getMessage(), startedAt));
            throw new AiOutlineException("AI_RESPONSE_INVALID", "AI 返回正文结构无效，请重试");
        }
    }

    private void validateRequest(AiParagraphRequest request) {
        if (request == null || request.heading() == null || request.heading().isBlank()) {
            throw new AiOutlineException("AI_PARAGRAPH_HEADING_REQUIRED", "段落标题不能为空");
        }
        if (request.instruction() != null && request.instruction().length() > MAX_INSTRUCTION_LENGTH) {
            throw new AiOutlineException("AI_PARAGRAPH_INSTRUCTION_TOO_LONG", "补充要求不能超过 1000 字");
        }
    }

    private List<DraftBlockUpdateRequest> upsertParagraphBlock(DraftDetailDto draft, String content, int sortOrder) {
        List<DraftBlockUpdateRequest> blocks = new ArrayList<>();
        boolean replaced = false;
        for (DraftBlockDto block : draft.blocks()) {
            if ("BODY_PARAGRAPH".equals(block.blockType())
                    && (block.sortOrder() == sortOrder || (!replaced && isBlank(block.content())))) {
                blocks.add(new DraftBlockUpdateRequest("BODY_PARAGRAPH", content, sortOrder));
                replaced = true;
            } else {
                blocks.add(new DraftBlockUpdateRequest(block.blockType(), block.content(), block.sortOrder()));
            }
        }
        if (!replaced) {
            blocks.add(new DraftBlockUpdateRequest("BODY_PARAGRAPH", content, sortOrder));
        }
        return blocks.stream()
                .sorted(Comparator.comparing(DraftBlockUpdateRequest::sortOrder))
                .toList();
    }

    private int nextBodySortOrder(DraftDetailDto draft) {
        return draft.blocks().stream()
                .mapToInt(DraftBlockDto::sortOrder)
                .max()
                .orElse(20) + 10;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AiGenerationTrace successTrace(
            UUID traceId,
            long draftId,
            ParagraphPrompt prompt,
            DraftBlockDto block,
            Instant startedAt
    ) {
        return new AiGenerationTrace(
                traceId,
                draftId,
                "PARAGRAPH",
                modelAdapter.provider(),
                modelAdapter.modelName(),
                "SUCCESS",
                prompt.promptVersion(),
                prompt.inputSummary(),
                "blockType=%s;sortOrder=%d;contentChars=%d".formatted(
                        block.blockType(),
                        block.sortOrder(),
                        block.content().length()
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
            ParagraphPrompt prompt,
            String errorCode,
            String errorMessage,
            Instant startedAt
    ) {
        return new AiGenerationTrace(
                traceId,
                draftId,
                "PARAGRAPH",
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
