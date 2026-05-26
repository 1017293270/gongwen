package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {
    public static final String OUTLINE_PROMPT_VERSION = "outline-v1";
    public static final String PARAGRAPH_PROMPT_VERSION = "paragraph-v1";
    private static final int BLOCK_TEXT_LIMIT = 160;
    private static final int MATERIAL_TEXT_LIMIT = 240;

    public OutlinePrompt buildOutlinePrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            String instruction
    ) {
        List<String> fieldSummaries = draft.blocks().stream()
                .map(block -> block.blockType() + ": " + summarize(block.content(), BLOCK_TEXT_LIMIT))
                .toList();
        List<String> materialSummaries = materials.stream()
                .map(material -> material.originalFileName() + ": " + summarize(material.text(), MATERIAL_TEXT_LIMIT))
                .toList();
        String safeInstruction = instruction == null ? "" : instruction.strip();
        return new OutlinePrompt(
                OUTLINE_PROMPT_VERSION,
                draft.documentTypeCode(),
                draft.title(),
                fieldSummaries,
                materialSummaries,
                safeInstruction,
                "documentType=%s;draftBlocks=%d;materials=%d;materialSummaryChars=%d;instructionChars=%d".formatted(
                        draft.documentTypeCode(),
                        draft.blocks().size(),
                        materials.size(),
                        materialSummaries.stream().mapToInt(String::length).sum(),
                        safeInstruction.length()
                )
        );
    }

    public ParagraphPrompt buildParagraphPrompt(
            DraftDetailDto draft,
            List<MaterialPromptSummary> materials,
            AiParagraphRequest request
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
        return new ParagraphPrompt(
                PARAGRAPH_PROMPT_VERSION,
                draft.documentTypeCode(),
                draft.title(),
                heading,
                points,
                fieldSummaries,
                materialSummaries,
                instruction,
                "documentType=%s;draftBlocks=%d;materials=%d;materialSummaryChars=%d;headingChars=%d;points=%d;instructionChars=%d".formatted(
                        draft.documentTypeCode(),
                        draft.blocks().size(),
                        materials.size(),
                        materialSummaries.stream().mapToInt(String::length).sum(),
                        heading.length(),
                        points.size(),
                        instruction.length()
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
