package com.gongwen.assistant.draft.node;

import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingItem;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingProfile;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingRepository;
import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DraftNodeService {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "EMPTY",
            "USER_FILLED",
            "AI_GENERATED",
            "USER_MODIFIED_AFTER_AI",
            "NEEDS_REVIEW",
            "QUALITY_WARNING",
            "QUALITY_ERROR",
            "EXPORT_BLOCKED",
            "FORMAT_OVERRIDDEN",
            "LOCKED"
    );
    private static final Set<String> SKIPPED_ROLES = Set.of("IGNORE");
    private static final Set<String> PRESERVABLE_REINITIALIZE_STATUSES = Set.of(
            "USER_FILLED",
            "AI_GENERATED",
            "USER_MODIFIED_AFTER_AI",
            "FORMAT_OVERRIDDEN"
    );
    private static final Set<String> ALLOWED_ALIGNMENTS = Set.of("LEFT", "CENTER", "RIGHT", "BOTH", "JUSTIFY");
    private static final Set<String> ALLOWED_LINE_SPACING_RULES = Set.of("AUTO", "EXACT", "AT_LEAST");

    private final DraftService draftService;
    private final DraftNodeRepository draftNodeRepository;
    private final StructureMappingRepository mappingRepository;
    private final DocumentStructureProfileRepository structureProfileRepository;

    public DraftNodeService(
            DraftService draftService,
            DraftNodeRepository draftNodeRepository,
            StructureMappingRepository mappingRepository,
            DocumentStructureProfileRepository structureProfileRepository
    ) {
        this.draftService = draftService;
        this.draftNodeRepository = draftNodeRepository;
        this.mappingRepository = mappingRepository;
        this.structureProfileRepository = structureProfileRepository;
    }

    public List<DraftNodeDto> initializeNodes(long draftId) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        Long templateVersionId = draft.templateVersionId();
        if (templateVersionId == null) {
            throw new DraftNodeException("DRAFT_TEMPLATE_REQUIRED", "Draft must bind a template version before nodes can be initialized");
        }
        StructureMappingProfile mapping = mappingRepository.findLatestByStatus(templateVersionId, "PUBLISHED")
                .orElseThrow(() -> new DraftNodeException("STRUCTURE_MAPPING_REQUIRED", "Published structure mapping is required before nodes can be initialized"));
        List<DraftNode> existingNodes = draftNodeRepository.findByDraftId(draftId);
        if (!existingNodes.isEmpty()) {
            return toDtos(existingNodes);
        }
        DocumentStructureProfile structureProfile = structureProfileRepository.findByTemplateVersionId(templateVersionId)
                .orElseThrow(() -> new DraftNodeException("DOCUMENT_STRUCTURE_PROFILE_NOT_FOUND", "Document structure profile not found"));
        List<DraftNode> nodes = buildDraftNodes(draftId, draft, mapping, structureProfile, List.of(), false);
        return toDtos(draftNodeRepository.replaceForDraft(draftId, nodes));
    }

    public List<DraftNodeDto> reinitializeNodes(long draftId, ReinitializeDraftNodesRequest request) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        Long templateVersionId = draft.templateVersionId();
        if (templateVersionId == null) {
            throw new DraftNodeException("DRAFT_TEMPLATE_REQUIRED", "Draft must bind a template version before nodes can be reinitialized");
        }
        StructureMappingProfile mapping = mappingRepository.findLatestByStatus(templateVersionId, "PUBLISHED")
                .orElseThrow(() -> new DraftNodeException("STRUCTURE_MAPPING_REQUIRED", "Published structure mapping is required before nodes can be reinitialized"));
        DocumentStructureProfile structureProfile = structureProfileRepository.findByTemplateVersionId(templateVersionId)
                .orElseThrow(() -> new DraftNodeException("DOCUMENT_STRUCTURE_PROFILE_NOT_FOUND", "Document structure profile not found"));
        List<DraftNode> existingNodes = draftNodeRepository.findByDraftId(draftId);
        boolean preserveUserEditedNodes = request == null || request.shouldPreserveUserEditedNodes();
        List<DraftNode> nodes = buildDraftNodes(draftId, draft, mapping, structureProfile, existingNodes, preserveUserEditedNodes);
        return toDtos(draftNodeRepository.replaceForDraft(draftId, nodes));
    }

    private List<DraftNode> buildDraftNodes(
            long draftId,
            DraftDetailDto draft,
            StructureMappingProfile mapping,
            DocumentStructureProfile structureProfile,
            List<DraftNode> existingNodes,
            boolean preserveUserEditedNodes
    ) {
        Map<String, DocumentNode> sourceNodes = structureProfile.nodes().stream()
                .collect(Collectors.toMap(DocumentNode::nodeKey, Function.identity()));
        List<StructureMappingItem> sourceItems = mapping.items().stream()
                .filter(this::shouldCreateDraftNode)
                .sorted(Comparator.comparingInt(StructureMappingItem::sortOrder))
                .toList();
        Map<String, Long> sourceRoleCounts = sourceItems.stream()
                .collect(Collectors.groupingBy(StructureMappingItem::role, Collectors.counting()));
        Map<String, String> legacyContent = legacyBlockContent(draft, sourceRoleCounts);
        Map<Integer, String> legacyBodyBySortOrder = legacyBodyBySortOrder(draft);
        Map<String, DraftNode> existingByKeyAndRole = existingNodes.stream()
                .collect(Collectors.toMap(
                        node -> keyAndRole(node.templateNodeKey(), node.role()),
                        Function.identity(),
                        (first, ignored) -> first
                ));
        return sourceItems.stream()
                .map(item -> toDraftNode(
                        draftId,
                        mapping.mappingProfileId(),
                        item,
                        sourceNodes.get(item.nodeKey()),
                        legacyContent,
                        legacyBodyBySortOrder,
                        existingByKeyAndRole,
                        preserveUserEditedNodes
                ))
                .toList();
    }

    public List<DraftNodeDto> listNodes(long draftId) {
        draftService.getDraft(draftId);
        return toDtos(draftNodeRepository.findByDraftId(draftId));
    }

    public DraftNodeDto updateNode(long draftId, long nodeId, UpdateDraftNodeRequest request) {
        draftService.getDraft(draftId);
        String status = normalizeStatus(request == null ? null : request.status());
        String content = request == null || request.content() == null ? "" : request.content();
        DraftNode updated = draftNodeRepository.updateContent(draftId, nodeId, content, status)
                .orElseThrow(() -> new DraftNodeException("DRAFT_NODE_NOT_FOUND", "Draft node not found: " + nodeId));
        return DraftNodeDto.from(updated);
    }

    public DraftNodeDto saveFormatOverride(long draftId, long nodeId, DraftNodeFormatOverride request) {
        draftService.getDraft(draftId);
        DraftNodeFormatOverride override = normalizeFormatOverride(request);
        DraftNode updated = draftNodeRepository.updateFormatOverride(draftId, nodeId, override, "FORMAT_OVERRIDDEN")
                .orElseThrow(() -> new DraftNodeException("DRAFT_NODE_NOT_FOUND", "Draft node not found: " + nodeId));
        return DraftNodeDto.from(updated);
    }

    public DraftNodeDto restoreTemplateDefaultFormatting(long draftId, long nodeId) {
        draftService.getDraft(draftId);
        DraftNode existing = draftNodeRepository.findByDraftId(draftId).stream()
                .filter(node -> node.id() == nodeId)
                .findFirst()
                .orElseThrow(() -> new DraftNodeException("DRAFT_NODE_NOT_FOUND", "Draft node not found: " + nodeId));
        DraftNode updated = draftNodeRepository.updateFormatOverride(
                        draftId,
                        nodeId,
                        DraftNodeFormatOverride.empty(),
                        statusAfterFormatRestore(existing))
                .orElseThrow(() -> new DraftNodeException("DRAFT_NODE_NOT_FOUND", "Draft node not found: " + nodeId));
        return DraftNodeDto.from(updated);
    }

    private DraftNode toDraftNode(
            long draftId,
            Long mappingProfileId,
            StructureMappingItem item,
            DocumentNode sourceNode,
            Map<String, String> legacyContent,
            Map<Integer, String> legacyBodyBySortOrder,
            Map<String, DraftNode> existingByKeyAndRole,
            boolean preserveUserEditedNodes
    ) {
        String content = initialContent(item, sourceNode, legacyContent, legacyBodyBySortOrder);
        String title = titleFor(item.role(), sourceNode, content);
        DraftNode node = new DraftNode(
                0,
                draftId,
                mappingProfileId,
                item.nodeKey(),
                sourceNode == null ? null : sourceNode.parentKey(),
                sourceNode == null ? "PARAGRAPH" : sourceNode.nodeType(),
                item.role(),
                item.slotKey().isBlank() ? slotKeyFor(item.role()) : item.slotKey(),
                title,
                content,
                item.sortOrder(),
                initialStatus(item, content),
                DraftNodeFormatOverride.empty(),
                null,
                null
        );
        return preserveUserEditedNodes ? preserveExistingContent(node, existingByKeyAndRole) : node;
    }

    private String initialContent(
            StructureMappingItem item,
            DocumentNode sourceNode,
            Map<String, String> legacyContent,
            Map<Integer, String> legacyBodyBySortOrder
    ) {
        String role = item.role();
        String source = editableSourceText(sourceNode);
        if ("BODY".equals(role) || role.startsWith("BODY_HEADING_LEVEL_")) {
            if (!source.isBlank()) {
                return source;
            }
            return "BODY".equals(role) ? legacyBodyBySortOrder(item, legacyBodyBySortOrder) : "";
        }
        if ("ATTACHMENT_NOTE".equals(role) || "ATTACHMENT_CONTENT".equals(role)) {
            String legacyAttachment = legacyContent.getOrDefault(role, "");
            return source.isBlank() ? legacyAttachment : source;
        }
        if (isEditableSourceRole(role)) {
            String legacy = legacyContent.getOrDefault(role, "");
            return source.isBlank() ? legacy : source;
        }
        return source;
    }

    private String legacyBodyBySortOrder(StructureMappingItem item, Map<Integer, String> legacyBodyBySortOrder) {
        return legacyBodyBySortOrder.getOrDefault(item.sortOrder(), "");
    }

    private DraftNode preserveExistingContent(DraftNode node, Map<String, DraftNode> existingByKeyAndRole) {
        DraftNode existing = existingByKeyAndRole.get(keyAndRole(node.templateNodeKey(), node.role()));
        if (existing == null || !PRESERVABLE_REINITIALIZE_STATUSES.contains(existing.status())) {
            return node;
        }
        return new DraftNode(
                node.id(),
                node.draftId(),
                node.structureMappingProfileId(),
                node.templateNodeKey(),
                node.parentTemplateNodeKey(),
                node.nodeType(),
                node.role(),
                node.slotKey(),
                node.title(),
                existing.content(),
                node.sortOrder(),
                existing.status(),
                existing.formatOverride(),
                node.createdAt(),
                node.updatedAt()
        );
    }

    private boolean isEditableSourceRole(String role) {
        return "TITLE".equals(role)
                || "RECIPIENT".equals(role)
                || "SIGNATURE".equals(role)
                || "DATE".equals(role);
    }

    private String editableSourceText(DocumentNode sourceNode) {
        if (sourceNode == null || sourceNode.text() == null) {
            return "";
        }
        String text = sourceNode.text().strip();
        if (text.contains("{{") && text.contains("}}")) {
            return "";
        }
        return text;
    }

    private String titleFor(String role, DocumentNode sourceNode, String content) {
        if (role.startsWith("BODY_HEADING_LEVEL_")) {
            return content.isBlank() ? textPreview(sourceNode) : content;
        }
        if ("BODY".equals(role)) {
            return textPreview(sourceNode);
        }
        return switch (role) {
            case "TITLE" -> "标题";
            case "RECIPIENT" -> "主送";
            case "ATTACHMENT_NOTE", "ATTACHMENT_CONTENT" -> "附件";
            case "SIGNATURE" -> "落款";
            case "DATE" -> "日期";
            case "STATIC_TEXT", "UNKNOWN" -> textPreview(sourceNode);
            default -> textPreview(sourceNode).isBlank() ? role : textPreview(sourceNode);
        };
    }

    private String textPreview(DocumentNode sourceNode) {
        if (sourceNode == null) {
            return "";
        }
        String preview = sourceNode.textPreview();
        return preview == null || preview.isBlank() ? sourceNode.text() : preview;
    }

    private boolean shouldCreateDraftNode(StructureMappingItem item) {
        return item != null
                && !"IGNORED".equals(item.status())
                && !SKIPPED_ROLES.contains(item.role());
    }

    private String initialStatus(StructureMappingItem item, String content) {
        if ("UNKNOWN".equals(item.role()) || "NEEDS_REVIEW".equals(item.status())) {
            return "NEEDS_REVIEW";
        }
        if ("STATIC_TEXT".equals(item.role()) || !isEditableDraftRole(item.role())) {
            return "LOCKED";
        }
        return content == null || content.isBlank() ? "EMPTY" : "USER_FILLED";
    }

    private boolean isEditableDraftRole(String role) {
        return "BODY".equals(role)
                || role.startsWith("BODY_HEADING_LEVEL_")
                || isEditableSourceRole(role)
                || "ATTACHMENT_NOTE".equals(role)
                || "ATTACHMENT_CONTENT".equals(role);
    }

    private Map<String, String> legacyBlockContent(DraftDetailDto draft, Map<String, Long> confirmedRoleCounts) {
        return draft.blocks().stream()
                .filter(block -> confirmedRoleCounts.getOrDefault(legacyRoleForBlock(block.blockType()), 0L) == 1L)
                .collect(Collectors.toMap(
                        block -> legacyRoleForBlock(block.blockType()),
                        DraftBlockDto::content,
                        (first, ignored) -> first
                ));
    }

    private Map<Integer, String> legacyBodyBySortOrder(DraftDetailDto draft) {
        return draft.blocks().stream()
                .filter(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .collect(Collectors.toMap(
                        DraftBlockDto::sortOrder,
                        DraftBlockDto::content,
                        (first, ignored) -> first
                ));
    }

    private String legacyRoleForBlock(String blockType) {
        if (blockType == null) {
            return "";
        }
        return switch (blockType) {
            case "ATTACHMENT" -> "ATTACHMENT_NOTE";
            default -> blockType;
        };
    }

    private String keyAndRole(String nodeKey, String role) {
        return (nodeKey == null ? "" : nodeKey) + "::" + (role == null ? "" : role);
    }

    private List<DraftNodeDto> toDtos(List<DraftNode> nodes) {
        return nodes.stream()
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .map(DraftNodeDto::from)
                .toList();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "USER_FILLED";
        }
        String normalized = status.strip();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new DraftNodeException("DRAFT_NODE_STATUS_INVALID", "Draft node status is invalid: " + status);
        }
        return normalized;
    }

    private DraftNodeFormatOverride normalizeFormatOverride(DraftNodeFormatOverride request) {
        DraftNodeFormatOverride override = request == null ? DraftNodeFormatOverride.empty() : request;
        if (override.fontSizePt() != null && (override.fontSizePt() <= 0 || override.fontSizePt() > 200)) {
            throw new DraftNodeException("DRAFT_NODE_FORMAT_INVALID", "Draft node font size is invalid");
        }
        if (override.alignment() != null && !ALLOWED_ALIGNMENTS.contains(override.alignment())) {
            throw new DraftNodeException("DRAFT_NODE_FORMAT_INVALID", "Draft node alignment is invalid: " + override.alignment());
        }
        if (override.lineSpacingRule() != null && !ALLOWED_LINE_SPACING_RULES.contains(override.lineSpacingRule())) {
            throw new DraftNodeException("DRAFT_NODE_FORMAT_INVALID", "Draft node line spacing rule is invalid: " + override.lineSpacingRule());
        }
        return override;
    }

    private String statusAfterFormatRestore(DraftNode node) {
        if ("LOCKED".equals(node.status())) {
            return "LOCKED";
        }
        return node.content().isBlank() ? "EMPTY" : "USER_FILLED";
    }

    private String slotKeyFor(String role) {
        return switch (role) {
            case "TITLE" -> "title";
            case "RECIPIENT" -> "recipient";
            case "BODY", "BODY_HEADING_LEVEL_1", "BODY_HEADING_LEVEL_2", "BODY_HEADING_LEVEL_3" -> "body";
            case "ATTACHMENT_NOTE", "ATTACHMENT_CONTENT", "TABLE_ATTACHMENT" -> "attachment";
            case "SIGNATURE" -> "signature";
            case "DATE" -> "date";
            default -> "";
        };
    }
}
