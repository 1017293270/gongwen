package com.gongwen.assistant.draft.node;

import com.gongwen.assistant.documentstructure.DocumentHeadingRoleDetector;
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
                                (first, ignored) -> first,
                                LinkedHashMap::new
                        )))
                .orElseGet(LinkedHashMap::new);
        Map<String, TemplateStructureFormattingProfile> templateOverrides = formattingRepository.findOverrides(templateVersionId);
        Map<String, TemplateStructureFormattingProfile> safeTemplateOverrides = templateOverrides == null
                ? Map.of()
                : templateOverrides;

        Map<Long, ResolvedDraftNodeFormatting> resolved = new LinkedHashMap<>();
        for (DraftNode node : nodes) {
            String sourceKey = sourceKeyFor(node, sourceNodes);
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

    private String sourceKeyFor(DraftNode node, Map<String, DocumentNode> sourceNodes) {
        DraftNodeMetadata metadata = node.metadata();
        if (metadata != null && metadata.synthetic() && !metadata.styleSourceNodeKey().isBlank()) {
            String nodeRole = normalizeRole(node.role());
            if (DocumentHeadingRoleDetector.isHeadingRole(nodeRole)) {
                String sourceTextRole = headingRoleForSourceKey(sourceNodes, metadata.styleSourceNodeKey());
                if (nodeRole.equals(sourceTextRole)) {
                    return metadata.styleSourceNodeKey();
                }
                if (!sourceTextRole.isBlank() && !nodeRole.equals(sourceTextRole)) {
                    return headingSourceKeyForRole(sourceNodes, nodeRole, node.templateNodeKey());
                }
            }
            String sourceRole = normalizeRole(sourceNodes.get(metadata.styleSourceNodeKey()));
            if (DocumentHeadingRoleDetector.isHeadingRole(nodeRole)
                    && DocumentHeadingRoleDetector.isHeadingRole(sourceRole)
                    && !nodeRole.equals(sourceRole)) {
                return headingSourceKeyForRole(sourceNodes, nodeRole, node.templateNodeKey());
            }
            return metadata.styleSourceNodeKey();
        }
        return node.templateNodeKey();
    }

    private String headingSourceKeyForRole(Map<String, DocumentNode> sourceNodes, String role, String fallbackKey) {
        String byNumbering = sourceNodes.values().stream()
                .filter(node -> role.equals(DocumentHeadingRoleDetector.detect(node.text())))
                .map(DocumentNode::nodeKey)
                .findFirst()
                .orElse("");
        if (!byNumbering.isBlank()) {
            return byNumbering;
        }
        String exact = sourceKeyForRole(sourceNodes, role);
        if (!exact.isBlank() && headingRoleForSourceKey(sourceNodes, exact).isBlank()) {
            return exact;
        }
        String bodySourceKey = sourceKeyForRole(sourceNodes, "BODY");
        return bodySourceKey.isBlank() ? fallbackKey : bodySourceKey;
    }

    private String headingRoleForSourceKey(Map<String, DocumentNode> sourceNodes, String sourceKey) {
        DocumentNode sourceNode = sourceNodes.get(sourceKey);
        return sourceNode == null ? "" : DocumentHeadingRoleDetector.detect(sourceNode.text());
    }

    private String sourceKeyForRole(Map<String, DocumentNode> sourceNodes, String role) {
        if (sourceNodes == null || sourceNodes.isEmpty()) {
            return "";
        }
        String normalizedRole = normalizeRole(role);
        return sourceNodes.values().stream()
                .filter(node -> normalizedRole.equals(normalizeRole(node)))
                .map(DocumentNode::nodeKey)
                .findFirst()
                .orElse("");
    }

    private String normalizeRole(DocumentNode node) {
        return node == null ? "" : normalizeRole(node.roleSuggestion());
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.strip().toUpperCase();
    }

    public record ResolvedDraftNodeFormatting(
            TemplateStructureFormattingProfile baseFormatting,
            TemplateStructureFormattingProfile effectiveFormatting
    ) {
    }
}
