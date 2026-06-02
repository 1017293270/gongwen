package com.gongwen.assistant.draft.node;

import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DraftNodeFormattingResolver {
    private final DocumentStructureProfileRepository structureProfileRepository;
    private final TemplateStructureFormattingRepository formattingRepository;
    private final TemplateEffectiveFormattingService effectiveFormattingService;

    public DraftNodeFormattingResolver(
            DocumentStructureProfileRepository structureProfileRepository,
            TemplateStructureFormattingRepository formattingRepository,
            TemplateEffectiveFormattingService effectiveFormattingService
    ) {
        this.structureProfileRepository = structureProfileRepository;
        this.formattingRepository = formattingRepository;
        this.effectiveFormattingService = effectiveFormattingService;
    }

    public Map<Long, ResolvedDraftNodeFormatting> resolve(Long templateVersionId, List<DraftNode> nodes) {
        if (templateVersionId == null || nodes == null || nodes.isEmpty()) {
            return Map.of();
        }
        Map<String, DocumentNode> sourceNodes = structureProfileRepository.findByTemplateVersionId(templateVersionId)
                .map(profile -> profile.nodes().stream()
                        .collect(Collectors.toMap(
                                DocumentNode::nodeKey,
                                Function.identity(),
                                (first, ignored) -> first
                        )))
                .orElse(Map.of());
        Map<String, TemplateStructureFormattingProfile> templateOverrides = formattingRepository.findOverrides(templateVersionId);
        Map<String, TemplateStructureFormattingProfile> safeTemplateOverrides = templateOverrides == null
                ? Map.of()
                : templateOverrides;

        Map<Long, ResolvedDraftNodeFormatting> resolved = new LinkedHashMap<>();
        for (DraftNode node : nodes) {
            String sourceKey = sourceKeyFor(node);
            DocumentNode sourceNode = sourceNodes.get(sourceKey);
            TemplateStructureFormattingProfile originalFormatting = sourceNode == null ? null : sourceNode.formatting();
            TemplateStructureFormattingProfile structureOverride = safeTemplateOverrides.get(sourceKey);
            TemplateStructureFormattingProfile baseFormatting = effectiveFormattingService.resolveDraftNodeFormatting(
                    null,
                    null,
                    originalFormatting,
                    structureOverride,
                    DraftNodeFormatOverride.empty()
            );
            TemplateStructureFormattingProfile effectiveFormatting = effectiveFormattingService.resolveDraftNodeFormatting(
                    null,
                    null,
                    originalFormatting,
                    structureOverride,
                    node.formatOverride()
            );
            resolved.put(node.id(), new ResolvedDraftNodeFormatting(baseFormatting, effectiveFormatting));
        }
        return resolved;
    }

    private String sourceKeyFor(DraftNode node) {
        DraftNodeMetadata metadata = node.metadata();
        if (metadata != null && metadata.synthetic() && !metadata.styleSourceNodeKey().isBlank()) {
            return metadata.styleSourceNodeKey();
        }
        return node.templateNodeKey();
    }

    public record ResolvedDraftNodeFormatting(
            TemplateStructureFormattingProfile baseFormatting,
            TemplateStructureFormattingProfile effectiveFormatting
    ) {
    }
}
