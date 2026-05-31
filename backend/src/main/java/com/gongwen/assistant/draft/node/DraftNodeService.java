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
import java.util.Objects;
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
    private static final Set<String> SKIPPED_ROLES = Set.of("UNKNOWN", "IGNORE");
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
        if (!existingNodes.isEmpty() && existingNodes.stream()
                .allMatch(node -> Objects.equals(node.structureMappingProfileId(), mapping.mappingProfileId()))) {
            return toDtos(existingNodes);
        }
        DocumentStructureProfile structureProfile = structureProfileRepository.findByTemplateVersionId(templateVersionId)
                .orElseThrow(() -> new DraftNodeException("DOCUMENT_STRUCTURE_PROFILE_NOT_FOUND", "Document structure profile not found"));
        Map<String, DocumentNode> sourceNodes = structureProfile.nodes().stream()
                .collect(Collectors.toMap(DocumentNode::nodeKey, Function.identity()));
        Map<String, String> legacyContent = legacyBlockContent(draft);
        List<DraftNode> nodes = mapping.items().stream()
                .filter(item -> "CONFIRMED".equals(item.status()))
                .filter(item -> !SKIPPED_ROLES.contains(item.role()))
                .sorted(Comparator.comparingInt(StructureMappingItem::sortOrder))
                .map(item -> toDraftNode(draftId, mapping.mappingProfileId(), item, sourceNodes.get(item.nodeKey()), legacyContent))
                .toList();
        return toDtos(draftNodeRepository.replaceForDraft(draftId, nodes));
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
            Map<String, String> legacyContent
    ) {
        String content = initialContent(item.role(), sourceNode, legacyContent);
        String title = titleFor(item.role(), sourceNode, content);
        return new DraftNode(
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
                initialStatus(item.role(), content),
                DraftNodeFormatOverride.empty(),
                null,
                null
        );
    }

    private String initialContent(String role, DocumentNode sourceNode, Map<String, String> legacyContent) {
        String legacy = legacyContent.getOrDefault(role, "");
        if (!legacy.isBlank()) {
            return legacy;
        }
        if ("BODY".equals(role)) {
            String legacyBody = legacyContent.getOrDefault("BODY_PARAGRAPH", "");
            return legacyBody.isBlank() ? editableSourceText(sourceNode) : legacyBody;
        }
        if ("ATTACHMENT_NOTE".equals(role) || "ATTACHMENT_CONTENT".equals(role)) {
            String legacyAttachment = legacyContent.getOrDefault("ATTACHMENT", "");
            return legacyAttachment.isBlank() ? editableSourceText(sourceNode) : legacyAttachment;
        }
        if (isEditableSourceRole(role)) {
            return editableSourceText(sourceNode);
        }
        if ("STATIC_TEXT".equals(role)
                || role.startsWith("BODY_HEADING_LEVEL_")) {
            return editableSourceText(sourceNode);
        }
        return "";
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
            case "STATIC_TEXT" -> textPreview(sourceNode);
            default -> role;
        };
    }

    private String textPreview(DocumentNode sourceNode) {
        if (sourceNode == null) {
            return "";
        }
        String preview = sourceNode.textPreview();
        return preview == null || preview.isBlank() ? sourceNode.text() : preview;
    }

    private String initialStatus(String role, String content) {
        if ("STATIC_TEXT".equals(role)) {
            return "LOCKED";
        }
        return content == null || content.isBlank() ? "EMPTY" : "USER_FILLED";
    }

    private Map<String, String> legacyBlockContent(DraftDetailDto draft) {
        return draft.blocks().stream()
                .collect(Collectors.toMap(
                        DraftBlockDto::blockType,
                        DraftBlockDto::content,
                        (first, ignored) -> first
                ));
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
