package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {
    public static final String OUTLINE_PROMPT_VERSION = "outline-v2";
    public static final String PARAGRAPH_PROMPT_VERSION = "paragraph-v1";
    public static final String LOCAL_OPERATION_PROMPT_VERSION = "local-operation-v1";
    public static final String QUALITY_CHECK_PROMPT_VERSION = "quality-check-v1";
    private static final int BLOCK_TEXT_LIMIT = 160;
    private static final int MATERIAL_TEXT_LIMIT = 240;
    private static final int OUTLINE_MATERIAL_TEXT_LIMIT = 600;

    public OutlinePrompt buildOutlinePrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            String instruction
    ) {
        return buildOutlinePrompt(draft, materials, instruction, AiNodeContext.none());
    }

    public OutlinePrompt buildOutlinePrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            String instruction,
            AiNodeContext nodeContext
    ) {
        List<String> fieldSummaries = draft.blocks().stream()
                .map(block -> block.blockType() + ": " + summarize(block.content(), BLOCK_TEXT_LIMIT))
                .toList();
        List<String> materialSummaries = materials.stream()
                .map(material -> "materialId=%d;file=%s;summary=%s".formatted(
                        material.id(),
                        material.originalFileName(),
                        summarize(material.text(), OUTLINE_MATERIAL_TEXT_LIMIT)
                ))
                .toList();
        String safeInstruction = instruction == null ? "" : instruction.strip();
        AiNodeContext safeNodeContext = nodeContext == null ? AiNodeContext.none() : nodeContext;
        return new OutlinePrompt(
                OUTLINE_PROMPT_VERSION,
                draft.documentTypeCode(),
                draft.title(),
                fieldSummaries,
                materialSummaries,
                safeInstruction,
                "documentType=%s;draftBlocks=%d;materials=%d;materialSummaryChars=%d;instructionChars=%d;%s".formatted(
                        draft.documentTypeCode(),
                        draft.blocks().size(),
                        materials.size(),
                        materialSummaries.stream().mapToInt(String::length).sum(),
                        safeInstruction.length(),
                        safeNodeContext.traceSummary()
                ),
                safeNodeContext
        );
    }

    public ParagraphPrompt buildParagraphPrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            AiParagraphRequest request
    ) {
        return buildParagraphPrompt(draft, materials, request, AiNodeContext.none());
    }

    public ParagraphPrompt buildParagraphPrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            AiParagraphRequest request,
            AiNodeContext nodeContext
    ) {
        return buildParagraphPrompt(draft, materials, request, nodeContext, "");
    }

    public ParagraphPrompt buildParagraphPrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            AiParagraphRequest request,
            AiNodeContext nodeContext,
            String formattingSummary
    ) {
        List<String> fieldSummaries = draft.blocks().stream()
                .map(block -> block.blockType() + ": " + summarize(block.content(), BLOCK_TEXT_LIMIT))
                .toList();
        List<String> materialSummaries = materials.stream()
                .map(material -> material.originalFileName() + ": " + summarize(material.text(), MATERIAL_TEXT_LIMIT))
                .toList();
        String heading = request == null || request.heading() == null ? "" : request.heading().strip();
        List<String> points = request == null ? List.of() : request.points().stream()
                .filter(point -> point != null && !point.isBlank())
                .map(String::strip)
                .toList();
        String instruction = request == null || request.instruction() == null ? "" : request.instruction().strip();
        AiNodeContext safeNodeContext = nodeContext == null ? AiNodeContext.none() : nodeContext;
        String safeFormattingSummary = formattingSummary == null ? "" : formattingSummary.strip();
        return new ParagraphPrompt(
                PARAGRAPH_PROMPT_VERSION,
                draft.documentTypeCode(),
                draft.title(),
                heading,
                points,
                fieldSummaries,
                materialSummaries,
                instruction,
                "documentType=%s;draftBlocks=%d;materials=%d;materialSummaryChars=%d;headingChars=%d;points=%d;instructionChars=%d;%s;formattingChars=%d".formatted(
                        draft.documentTypeCode(),
                        draft.blocks().size(),
                        materials.size(),
                        materialSummaries.stream().mapToInt(String::length).sum(),
                        heading.length(),
                        points.size(),
                        instruction.length(),
                        safeNodeContext.traceSummary(),
                        safeFormattingSummary.length()
                ),
                safeNodeContext,
                safeFormattingSummary
        );
    }

    public LocalOperationPrompt buildLocalOperationPrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            DraftBlockDto targetBlock,
            AiLocalOperationRequest request
    ) {
        List<String> fieldSummaries = draft.blocks().stream()
                .map(block -> block.blockType() + ": " + summarize(block.content(), BLOCK_TEXT_LIMIT))
                .toList();
        List<String> materialSummaries = materials.stream()
                .map(material -> material.originalFileName() + ": " + summarize(material.text(), MATERIAL_TEXT_LIMIT))
                .toList();
        String instruction = request == null || request.instruction() == null ? "" : request.instruction().strip();
        String originalText = targetBlock.content() == null ? "" : targetBlock.content().strip();
        return new LocalOperationPrompt(
                LOCAL_OPERATION_PROMPT_VERSION,
                draft.documentTypeCode(),
                draft.title(),
                targetBlock.id(),
                targetBlock.sortOrder(),
                request.operationType(),
                originalText,
                fieldSummaries,
                materialSummaries,
                instruction,
                "documentType=%s;targetBlockId=%d;targetSortOrder=%d;operationType=%s;originalChars=%d;materials=%d;materialSummaryChars=%d;instructionChars=%d".formatted(
                        draft.documentTypeCode(),
                        targetBlock.id(),
                        targetBlock.sortOrder(),
                        request.operationType(),
                        originalText.length(),
                        materials.size(),
                        materialSummaries.stream().mapToInt(String::length).sum(),
                        instruction.length()
                )
        );
    }

    public LocalOperationPrompt buildLocalOperationPrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            AiNodeContext nodeContext,
            int targetSortOrder,
            AiLocalOperationRequest request
    ) {
        List<String> fieldSummaries = draft.blocks().stream()
                .map(block -> block.blockType() + ": " + summarize(block.content(), BLOCK_TEXT_LIMIT))
                .toList();
        List<String> materialSummaries = materials.stream()
                .map(material -> material.originalFileName() + ": " + summarize(material.text(), MATERIAL_TEXT_LIMIT))
                .toList();
        String instruction = request == null || request.instruction() == null ? "" : request.instruction().strip();
        AiNodeContext safeNodeContext = nodeContext == null ? AiNodeContext.none() : nodeContext;
        String originalText = safeNodeContext.nodeContext() == null ? "" : safeNodeContext.nodeContext().strip();
        if (originalText.isBlank() && safeNodeContext.nodeId() != null) {
            String targetTitle = safeNodeContext.nodeTitle().isBlank() ? "当前结构节点" : safeNodeContext.nodeTitle();
            originalText = "当前结构节点正文为空，请围绕“" + targetTitle + "”生成可直接填入该节点的正文建议。";
        }
        return new LocalOperationPrompt(
                LOCAL_OPERATION_PROMPT_VERSION,
                draft.documentTypeCode(),
                draft.title(),
                0L,
                targetSortOrder,
                request.operationType(),
                originalText,
                fieldSummaries,
                materialSummaries,
                instruction,
                "documentType=%s;targetNodeId=%s;targetNodeRole=%s;targetSortOrder=%d;operationType=%s;originalChars=%d;materials=%d;materialSummaryChars=%d;instructionChars=%d".formatted(
                        draft.documentTypeCode(),
                        safeNodeContext.nodeId() == null ? "" : safeNodeContext.nodeId(),
                        safeNodeContext.nodeRole(),
                        targetSortOrder,
                        request.operationType(),
                        originalText.length(),
                        materials.size(),
                        materialSummaries.stream().mapToInt(String::length).sum(),
                        instruction.length()
                ),
                safeNodeContext.nodeId(),
                safeNodeContext.nodeRole(),
                safeNodeContext.nodeTitle(),
                safeNodeContext.nodeContext()
        );
    }

    public QualityCheckPrompt buildQualityCheckPrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            List<String> ruleSummaries
    ) {
        List<String> fieldSummaries = draft.blocks().stream()
                .filter(block -> !"BODY_PARAGRAPH".equals(block.blockType()))
                .map(block -> block.blockType() + ": " + summarize(block.content(), BLOCK_TEXT_LIMIT))
                .toList();
        List<String> bodySummaries = draft.blocks().stream()
                .filter(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .map(block -> "sortOrder=" + block.sortOrder() + ": " + summarize(block.content(), BLOCK_TEXT_LIMIT))
                .toList();
        List<String> materialSummaries = materials.stream()
                .map(material -> material.originalFileName() + ": " + summarize(material.text(), MATERIAL_TEXT_LIMIT))
                .toList();
        List<String> safeRuleSummaries = ruleSummaries == null ? List.of() : ruleSummaries.stream()
                .filter(summary -> summary != null && !summary.isBlank())
                .map(String::strip)
                .toList();
        return new QualityCheckPrompt(
                QUALITY_CHECK_PROMPT_VERSION,
                draft.documentTypeCode(),
                draft.title(),
                fieldSummaries,
                bodySummaries,
                materialSummaries,
                safeRuleSummaries,
                "documentType=%s;draftBlocks=%d;bodyBlocks=%d;materials=%d;materialSummaryChars=%d;ruleItems=%d".formatted(
                        draft.documentTypeCode(),
                        draft.blocks().size(),
                        bodySummaries.size(),
                        materials.size(),
                        materialSummaries.stream().mapToInt(String::length).sum(),
                        safeRuleSummaries.size()
                )
        );
    }

    private String summarize(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }
}
