package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.draft.UpdateDraftBlocksRequest;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeDto;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
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
    private static final List<String> PROTECTED_GENERATION_ROLES = List.of("TITLE", "RECIPIENT", "SIGNATURE", "DATE");

    private final DraftService draftService;
    private final MaterialRepository materialRepository;
    private final PromptBuilder promptBuilder;
    private final ModelAdapter modelAdapter;
    private final AiGenerationTraceRepository traceRepository;
    private final DraftNodeRepository draftNodeRepository;

    public AiParagraphService(
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

    public AiParagraphResponse generateParagraph(long draftId, AiParagraphRequest request) {
        validateRequest(request);
        DraftDetailDto draft = draftService.getDraft(draftId);
        DraftNode targetNode = resolveTargetNode(draftId, request);
        validateNodeTarget(request, targetNode);
        AiNodeContext nodeContext = paragraphNodeContext(request, targetNode);
        List<MaterialPromptSummary> materials = materialRepository.findReadyTextSummariesByDraftId(draftId);
        ParagraphPrompt prompt = promptBuilder.buildParagraphPrompt(draft, materials, request, nodeContext);
        UUID traceId = UUID.randomUUID();
        Instant startedAt = Instant.now();

        try {
            AiParagraphModelResponse modelResponse = modelAdapter.generateParagraph(prompt);
            String paragraphContent = normalizeParagraphContent(modelResponse.content(), prompt.heading());
            int sortOrder = request.sortOrder() == null || request.sortOrder() <= 0
                    ? nextBodySortOrder(draft)
                    : request.sortOrder();
            DraftDetailDto updatedDraft = draftService.updateBlocks(
                    draftId,
                    new UpdateDraftBlocksRequest(upsertParagraphBlock(draft, paragraphContent, sortOrder))
            );
            DraftBlockDto generatedBlock = updatedDraft.blocks().stream()
                    .filter(block -> "BODY_PARAGRAPH".equals(block.blockType()) && block.sortOrder() == sortOrder)
                    .findFirst()
                    .orElseThrow();
            DraftNodeDto updatedNode = updateTargetNode(draftId, targetNode, paragraphContent);
            traceRepository.save(successTrace(traceId, draftId, prompt, generatedBlock, updatedNode, startedAt));
            return new AiParagraphResponse(traceId, updatedDraft, generatedBlock, updatedNode);
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

    private DraftNode resolveTargetNode(long draftId, AiParagraphRequest request) {
        if (request == null || request.nodeId() == null) {
            return null;
        }
        return draftNodeRepository.findByDraftId(draftId).stream()
                .filter(candidate -> candidate.id() == request.nodeId())
                .findFirst()
                .orElseThrow(() -> new AiOutlineException("AI_NODE_TARGET_NOT_FOUND", "目标结构节点不存在"));
    }

    private void validateNodeTarget(AiParagraphRequest request, DraftNode targetNode) {
        String role = targetNode == null ? normalizeRole(request.nodeRole()) : normalizeRole(targetNode.role());
        if (role.isBlank()) {
            return;
        }
        if (PROTECTED_GENERATION_ROLES.contains(role)) {
            throw new AiOutlineException("AI_PARAGRAPH_NODE_ROLE_UNSUPPORTED", "段落生成不能直接改写标题、主送、落款或日期节点");
        }
        if (targetNode != null && !isBodyRole(role)) {
            throw new AiOutlineException("AI_PARAGRAPH_NODE_ROLE_UNSUPPORTED", "段落生成只能写入正文结构节点");
        }
    }

    private AiNodeContext paragraphNodeContext(AiParagraphRequest request, DraftNode targetNode) {
        if (request == null && targetNode == null) {
            return AiNodeContext.none();
        }
        return new AiNodeContext(
                targetNode == null ? request.nodeId() : Long.valueOf(targetNode.id()),
                firstNonBlank(targetNode == null ? null : targetNode.role(), request == null ? null : request.nodeRole()),
                firstNonBlank(targetNode == null ? null : targetNode.title(), request == null ? null : request.nodeTitle()),
                firstNonBlank(request == null ? null : request.nodeContext(), targetNode == null ? null : targetNode.content())
        );
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

    private DraftNodeDto updateTargetNode(long draftId, DraftNode targetNode, String content) {
        if (targetNode == null) {
            return null;
        }
        DraftNode updated = draftNodeRepository.updateContent(draftId, targetNode.id(), content, "AI_GENERATED")
                .orElseThrow(() -> new AiOutlineException("AI_NODE_TARGET_NOT_FOUND", "目标结构节点不存在"));
        return DraftNodeDto.from(updated);
    }

    private String normalizeParagraphContent(String content, String heading) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        String normalized = content.strip();
        String normalizedHeading = heading == null ? "" : heading.strip();
        if (normalizedHeading.isBlank() || normalized.startsWith(normalizedHeading)) {
            return normalized;
        }
        String separator = startsWithPunctuation(normalized) ? "" : "：";
        return normalizedHeading + separator + normalized;
    }

    private boolean startsWithPunctuation(String value) {
        return value.startsWith("：")
                || value.startsWith(":")
                || value.startsWith("，")
                || value.startsWith(",")
                || value.startsWith("。")
                || value.startsWith("；")
                || value.startsWith(";")
                || value.startsWith("、");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private AiGenerationTrace successTrace(
            UUID traceId,
            long draftId,
            ParagraphPrompt prompt,
            DraftBlockDto block,
            DraftNodeDto node,
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
                "blockType=%s;sortOrder=%d;contentChars=%d;nodeId=%s;nodeRole=%s".formatted(
                        block.blockType(),
                        block.sortOrder(),
                        block.content().length(),
                        node == null ? "" : node.id(),
                        node == null ? "" : node.role()
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

    private boolean isBodyRole(String role) {
        return "BODY".equals(role) || role.startsWith("BODY_HEADING_LEVEL_");
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.strip();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        return second == null ? "" : second.strip();
    }
}
