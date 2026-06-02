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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
            "LOCKED",
            "DELETED"
    );
    private static final Set<String> SKIPPED_ROLES = Set.of("IGNORE");
    private static final List<String> INSERTABLE_BODY_ROLES = List.of(
            "BODY_HEADING_LEVEL_1",
            "BODY_HEADING_LEVEL_2",
            "BODY_HEADING_LEVEL_3",
            "BODY"
    );
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
    private final DraftNodeFormattingResolver formattingResolver;

    @Autowired
    public DraftNodeService(
            DraftService draftService,
            DraftNodeRepository draftNodeRepository,
            StructureMappingRepository mappingRepository,
            DocumentStructureProfileRepository structureProfileRepository,
            DraftNodeFormattingResolver formattingResolver
    ) {
        this.draftService = draftService;
        this.draftNodeRepository = draftNodeRepository;
        this.mappingRepository = mappingRepository;
        this.structureProfileRepository = structureProfileRepository;
        this.formattingResolver = Objects.requireNonNull(formattingResolver, "formattingResolver is required");
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
            return toDtos(draft, existingNodes);
        }
        DocumentStructureProfile structureProfile = structureProfileRepository.findByTemplateVersionId(templateVersionId)
                .orElseThrow(() -> new DraftNodeException("DOCUMENT_STRUCTURE_PROFILE_NOT_FOUND", "Document structure profile not found"));
        List<DraftNode> nodes = buildDraftNodes(draftId, draft, mapping, structureProfile, List.of(), false);
        return toDtos(draft, draftNodeRepository.replaceForDraft(draftId, nodes));
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
        return toDtos(draft, draftNodeRepository.replaceForDraft(draftId, nodes));
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
        DraftDetailDto draft = draftService.getDraft(draftId);
        return toDtos(draft, draftNodeRepository.findByDraftId(draftId));
    }

    public List<DraftNodeRoleOptionDto> listInsertableRoles(long draftId) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        Long templateVersionId = draft.templateVersionId();
        if (templateVersionId == null) {
            throw new DraftNodeException("DRAFT_TEMPLATE_REQUIRED", "Draft must bind a template version before body structures can be inserted");
        }
        StructureMappingProfile mapping = mappingRepository.findLatestByStatus(templateVersionId, "PUBLISHED")
                .orElseThrow(() -> new DraftNodeException("STRUCTURE_MAPPING_REQUIRED", "Published structure mapping is required before body structures can be inserted"));
        Set<String> roles = new LinkedHashSet<>();
        mapping.items().stream()
                .map(StructureMappingItem::role)
                .filter(INSERTABLE_BODY_ROLES::contains)
                .forEach(roles::add);
        return INSERTABLE_BODY_ROLES.stream()
                .filter(roles::contains)
                .map(role -> new DraftNodeRoleOptionDto(role, labelForInsertableRole(role), role.startsWith("BODY_HEADING_LEVEL_")))
                .toList();
    }

    public List<DraftNodeDto> insertNode(long draftId, InsertDraftNodeRequest request) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        Long templateVersionId = draft.templateVersionId();
        if (templateVersionId == null) {
            throw new DraftNodeException("DRAFT_TEMPLATE_REQUIRED", "Draft must bind a template version before body structures can be inserted");
        }
        StructureMappingProfile mapping = mappingRepository.findLatestByStatus(templateVersionId, "PUBLISHED")
                .orElseThrow(() -> new DraftNodeException("STRUCTURE_MAPPING_REQUIRED", "Published structure mapping is required before body structures can be inserted"));
        String role = normalizeInsertRole(request == null ? null : request.role());
        String position = normalizeInsertPosition(request == null ? null : request.position());
        List<DraftNode> existingNodes = draftNodeRepository.findByDraftId(draftId);
        if (existingNodes.isEmpty()) {
            initializeNodes(draftId);
            existingNodes = draftNodeRepository.findByDraftId(draftId);
        }
        List<DraftNode> orderedNodes = existingNodes.stream()
                .filter(node -> !"DELETED".equalsIgnoreCase(node.status()))
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .toList();
        DraftNode anchor = anchorNode(orderedNodes, request == null ? null : request.anchorNodeId(), position);
        List<String> rolesToCreate = role.startsWith("BODY_HEADING_LEVEL_")
                ? List.of(role, "BODY")
                : List.of(role);
        String groupId = UUID.randomUUID().toString();
        String anchorKey = anchor == null ? "" : anchor.templateNodeKey();
        Long anchorId = anchor == null ? null : anchor.id();
        int baseSortOrder = firstInsertedSortOrder(orderedNodes, anchor, position, rolesToCreate.size());
        List<DraftNode> created = new ArrayList<>();
        for (int index = 0; index < rolesToCreate.size(); index++) {
            String createdRole = rolesToCreate.get(index);
            created.add(newSyntheticNode(
                    draftId,
                    mapping.mappingProfileId(),
                    createdRole,
                    baseSortOrder + index,
                    anchorId,
                    anchorKey,
                    position,
                    groupId,
                    styleSourceNodeKey(existingNodes, createdRole)
            ));
        }
        List<DraftNode> savedNodes = draftNodeRepository.insertNodes(draftId, created);
        return toDtos(draft, reorderAfterInsert(draftId, orderedNodes, savedNodes, groupId, anchor, position));
    }

    public DraftNodeDto updateNode(long draftId, long nodeId, UpdateDraftNodeRequest request) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        String status = normalizeStatus(request == null ? null : request.status());
        String content = request == null || request.content() == null ? "" : request.content();
        DraftNode updated = draftNodeRepository.updateContent(draftId, nodeId, content, status)
                .orElseThrow(() -> new DraftNodeException("DRAFT_NODE_NOT_FOUND", "Draft node not found: " + nodeId));
        return toDto(draft, updated);
    }

    public DraftNodeDto saveFormatOverride(long draftId, long nodeId, DraftNodeFormatOverride request) {
        DraftDetailDto draft = draftService.getDraft(draftId);
        DraftNodeFormatOverride override = normalizeFormatOverride(request);
        DraftNode updated = draftNodeRepository.updateFormatOverride(draftId, nodeId, override, "FORMAT_OVERRIDDEN")
                .orElseThrow(() -> new DraftNodeException("DRAFT_NODE_NOT_FOUND", "Draft node not found: " + nodeId));
        return toDto(draft, updated);
    }

    public DraftNodeDto restoreTemplateDefaultFormatting(long draftId, long nodeId) {
        DraftDetailDto draft = draftService.getDraft(draftId);
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
        return toDto(draft, updated);
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
                DraftNodeMetadata.empty(),
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
                existing.metadata(),
                node.createdAt(),
                node.updatedAt()
        );
    }

    private DraftNode newSyntheticNode(
            long draftId,
            Long mappingProfileId,
            String role,
            int sortOrder,
            Long anchorNodeId,
            String anchorTemplateNodeKey,
            String position,
            String groupId,
            String styleSourceNodeKey
    ) {
        return new DraftNode(
                0,
                draftId,
                mappingProfileId,
                "synthetic-" + UUID.randomUUID(),
                null,
                "PARAGRAPH",
                role,
                slotKeyFor(role),
                titleForSyntheticRole(role),
                "",
                sortOrder,
                "EMPTY",
                DraftNodeFormatOverride.empty(),
                DraftNodeMetadata.synthetic(anchorNodeId, anchorTemplateNodeKey, position, groupId, styleSourceNodeKey),
                null,
                null
        );
    }

    private DraftNode anchorNode(List<DraftNode> orderedNodes, Long anchorNodeId, String position) {
        if ("END_OF_BODY".equals(position)) {
            return orderedNodes.stream()
                    .filter(this::isBodyStructureNode)
                    .reduce((left, right) -> right)
                    .orElse(null);
        }
        if (anchorNodeId == null) {
            throw new DraftNodeException("DRAFT_NODE_ANCHOR_REQUIRED", "Anchor node is required when inserting before or after a body structure");
        }
        return orderedNodes.stream()
                .filter(node -> node.id() == anchorNodeId)
                .findFirst()
                .orElseThrow(() -> new DraftNodeException("DRAFT_NODE_NOT_FOUND", "Draft node not found: " + anchorNodeId));
    }

    private int firstInsertedSortOrder(List<DraftNode> orderedNodes, DraftNode anchor, String position, int createCount) {
        if (orderedNodes.isEmpty()) {
            return 10;
        }
        if ("END_OF_BODY".equals(position)) {
            DraftNode nextNonBody = nextNonBodyAfterAnchor(orderedNodes, anchor);
            if (anchor == null) {
                return 10;
            }
            return sortOrderBetween(anchor.sortOrder(), nextNonBody == null ? null : nextNonBody.sortOrder(), createCount, false);
        }
        if ("BEFORE".equals(position)) {
            DraftNode previous = previousNode(orderedNodes, anchor);
            return sortOrderBetween(previous == null ? null : previous.sortOrder(), anchor.sortOrder(), createCount, true);
        }
        DraftNode next = nextNode(orderedNodes, anchor);
        return sortOrderBetween(anchor.sortOrder(), next == null ? null : next.sortOrder(), createCount, false);
    }

    private int sortOrderBetween(Integer previous, Integer next, int createCount, boolean alignBeforeNext) {
        if (previous == null && next == null) {
            return 10;
        }
        if (previous == null) {
            return next - createCount;
        }
        if (next == null) {
            return previous + 10;
        }
        int gap = next - previous;
        if (gap > createCount) {
            return alignBeforeNext ? next - createCount : previous + 1;
        }
        return alignBeforeNext ? next - createCount : previous + 1;
    }

    private List<DraftNode> reorderAfterInsert(
            long draftId,
            List<DraftNode> orderedNodes,
            List<DraftNode> savedNodes,
            String groupId,
            DraftNode anchor,
            String position
    ) {
        List<DraftNode> savedCreatedNodes = savedNodes.stream()
                .filter(node -> groupId.equals(node.metadata().groupId()))
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .toList();
        if (savedCreatedNodes.isEmpty()) {
            return savedNodes;
        }
        List<DraftNode> reorderedNodes = new ArrayList<>(orderedNodes);
        int insertIndex = insertIndex(orderedNodes, anchor, position);
        reorderedNodes.addAll(insertIndex, savedCreatedNodes);

        Map<Long, Integer> sortOrdersByNodeId = new LinkedHashMap<>();
        for (int index = 0; index < reorderedNodes.size(); index++) {
            sortOrdersByNodeId.put(reorderedNodes.get(index).id(), (index + 1) * 10);
        }
        return draftNodeRepository.updateSortOrders(draftId, sortOrdersByNodeId);
    }

    private int insertIndex(List<DraftNode> orderedNodes, DraftNode anchor, String position) {
        if (anchor == null) {
            return orderedNodes.size();
        }
        for (int index = 0; index < orderedNodes.size(); index++) {
            if (orderedNodes.get(index).id() == anchor.id()) {
                return "BEFORE".equals(position) ? index : index + 1;
            }
        }
        return orderedNodes.size();
    }

    private DraftNode previousNode(List<DraftNode> orderedNodes, DraftNode anchor) {
        if (anchor == null) {
            return null;
        }
        for (int index = 0; index < orderedNodes.size(); index++) {
            if (orderedNodes.get(index).id() == anchor.id()) {
                return index == 0 ? null : orderedNodes.get(index - 1);
            }
        }
        return null;
    }

    private DraftNode nextNode(List<DraftNode> orderedNodes, DraftNode anchor) {
        if (anchor == null) {
            return null;
        }
        for (int index = 0; index < orderedNodes.size(); index++) {
            if (orderedNodes.get(index).id() == anchor.id()) {
                return index >= orderedNodes.size() - 1 ? null : orderedNodes.get(index + 1);
            }
        }
        return null;
    }

    private DraftNode nextNonBodyAfterAnchor(List<DraftNode> orderedNodes, DraftNode anchor) {
        if (anchor == null) {
            return null;
        }
        boolean afterAnchor = false;
        for (DraftNode node : orderedNodes) {
            if (afterAnchor && !isBodyStructureNode(node)) {
                return node;
            }
            if (node.id() == anchor.id()) {
                afterAnchor = true;
            }
        }
        return null;
    }

    private String normalizeInsertRole(String role) {
        if (role == null || role.isBlank()) {
            return "BODY";
        }
        String normalized = role.strip();
        if (!INSERTABLE_BODY_ROLES.contains(normalized)) {
            throw new DraftNodeException("DRAFT_NODE_ROLE_INVALID", "Only body structures can be inserted in the workbench");
        }
        return normalized;
    }

    private String normalizeInsertPosition(String position) {
        if (position == null || position.isBlank()) {
            return "END_OF_BODY";
        }
        String normalized = position.strip();
        if (!Set.of("BEFORE", "AFTER", "END_OF_BODY").contains(normalized)) {
            throw new DraftNodeException("DRAFT_NODE_INSERT_POSITION_INVALID", "Draft node insert position is invalid: " + position);
        }
        return normalized;
    }

    private boolean isBodyStructureNode(DraftNode node) {
        String role = node.role();
        return "BODY".equals(role) || role.startsWith("BODY_HEADING_LEVEL_");
    }

    private String styleSourceNodeKey(List<DraftNode> nodes, String role) {
        return nodes.stream()
                .filter(node -> role.equals(node.role()))
                .map(DraftNode::templateNodeKey)
                .filter(key -> key != null && !key.isBlank())
                .findFirst()
                .orElse("");
    }

    private String titleForSyntheticRole(String role) {
        return switch (role) {
            case "BODY_HEADING_LEVEL_1" -> "一级标题";
            case "BODY_HEADING_LEVEL_2" -> "二级标题";
            case "BODY_HEADING_LEVEL_3" -> "三级标题";
            case "BODY" -> "正文";
            default -> role;
        };
    }

    private String labelForInsertableRole(String role) {
        return switch (role) {
            case "BODY_HEADING_LEVEL_1" -> "一级标题 + 正文";
            case "BODY_HEADING_LEVEL_2" -> "二级标题 + 正文";
            case "BODY_HEADING_LEVEL_3" -> "三级标题 + 正文";
            case "BODY" -> "正文段落";
            default -> role;
        };
    }

    private boolean isEditableSourceRole(String role) {
        return "TITLE".equals(role)
                || "RECIPIENT".equals(role)
                || "ISSUING_ORGAN".equals(role)
                || "DOC_NUMBER".equals(role)
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
                || "ATTACHMENT_CONTENT".equals(role)
                || "TABLE_ATTACHMENT".equals(role);
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

    private DraftNodeDto toDto(DraftDetailDto draft, DraftNode node) {
        return toDtos(draft, List.of(node)).stream()
                .findFirst()
                .orElseThrow();
    }

    private List<DraftNodeDto> toDtos(DraftDetailDto draft, List<DraftNode> nodes) {
        Map<Long, DraftNodeFormattingResolver.ResolvedDraftNodeFormatting> formattingByNodeId =
                formattingResolver.resolve(draft.templateVersionId(), nodes);
        return nodes.stream()
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .map(node -> {
                    DraftNodeFormattingResolver.ResolvedDraftNodeFormatting formatting = formattingByNodeId.get(node.id());
                    return DraftNodeDto.from(
                            node,
                            formatting == null ? null : formatting.baseFormatting(),
                            formatting == null ? null : formatting.effectiveFormatting()
                    );
                })
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
            case "ISSUING_ORGAN" -> "issuingOrgan";
            case "DOC_NUMBER" -> "docNumber";
            case "SIGNATURE" -> "signature";
            case "DATE" -> "date";
            default -> "";
        };
    }
}
