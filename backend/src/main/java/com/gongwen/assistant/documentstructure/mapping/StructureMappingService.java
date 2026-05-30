package com.gongwen.assistant.documentstructure.mapping;

import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import com.gongwen.assistant.template.profile.TemplateAnalysisProfile;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StructureMappingService {
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ISSUING_ORGAN",
            "RED_HEADER",
            "DOC_NUMBER",
            "SIGNER",
            "TITLE",
            "RECIPIENT",
            "BODY",
            "BODY_HEADING_LEVEL_1",
            "BODY_HEADING_LEVEL_2",
            "BODY_HEADING_LEVEL_3",
            "ATTACHMENT_NOTE",
            "ATTACHMENT_CONTENT",
            "SIGNATURE",
            "DATE",
            "COPY_TO",
            "PRINT_ORGAN",
            "PRINT_DATE",
            "PAGE_NUMBER",
            "SEAL_OR_IMAGE",
            "TABLE_ATTACHMENT",
            "STATIC_TEXT",
            "IGNORE",
            "UNKNOWN"
    );
    private static final Set<String> ALLOWED_ITEM_STATUSES = Set.of("SUGGESTED", "CONFIRMED", "IGNORED", "NEEDS_REVIEW");
    private static final Set<String> ALLOWED_SOURCES = Set.of("RULE", "AI", "USER", "IMPORT", "SYSTEM");
    private static final Set<String> REQUIRED_TEMPLATE_ROLES = Set.of("TITLE", "BODY");
    private static final Set<String> NON_TEMPLATE_DOCUMENT_KINDS = Set.of(
            "MANUAL_OR_GUIDE",
            "POLICY_OR_REGULATION",
            "ORDINARY_DOCUMENT"
    );

    private final StructureMappingRepository mappingRepository;
    private final DocumentStructureProfileRepository structureProfileRepository;
    private final TemplateProfileRepository templateProfileRepository;
    private final CurrentUserProvider currentUserProvider;

    public StructureMappingService(
            StructureMappingRepository mappingRepository,
            DocumentStructureProfileRepository structureProfileRepository,
            TemplateProfileRepository templateProfileRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.mappingRepository = mappingRepository;
        this.structureProfileRepository = structureProfileRepository;
        this.templateProfileRepository = templateProfileRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public StructureMappingProfile getMapping(long templateVersionId) {
        requireTemplateAdmin();
        return mappingRepository.findLatest(templateVersionId)
                .orElseGet(() -> defaultDraft(templateVersionId, structureProfile(templateVersionId)));
    }

    public StructureMappingProfile saveDraft(long templateVersionId, SaveStructureMappingRequest request) {
        CurrentUser currentUser = requireTemplateAdmin();
        DocumentStructureProfile structureProfile = structureProfile(templateVersionId);
        List<StructureMappingItem> items = normalizeAndValidateItems(structureProfile, request.items());
        StructureMappingProfile draft = new StructureMappingProfile(
                null,
                templateVersionId,
                mappingRepository.nextVersionNo(templateVersionId),
                "DRAFT",
                items,
                List.of(),
                0,
                0,
                null,
                null,
                null
        );
        return mappingRepository.save(draft, currentUser);
    }

    public StructureMappingProfile publish(long templateVersionId, PublishStructureMappingRequest request) {
        CurrentUser currentUser = requireTemplateAdmin();
        DocumentStructureProfile structureProfile = structureProfile(templateVersionId);
        StructureMappingProfile draft = mappingRepository.findLatestByStatus(templateVersionId, "DRAFT")
                .orElseThrow(() -> new StructureMappingException("STRUCTURE_MAPPING_DRAFT_REQUIRED", "Structure mapping draft is required before publish"));
        List<StructureMappingItem> items = normalizeAndValidateItems(structureProfile, draft.items());
        List<StructureMappingValidationItem> blockers = publishBlockers(templateVersionId, items, request.adminOverride());
        if (!blockers.isEmpty()) {
            return new StructureMappingProfile(
                    draft.mappingProfileId(),
                    templateVersionId,
                    draft.versionNo(),
                    "DRAFT",
                    items,
                    blockers,
                    0,
                    0,
                    null,
                    draft.createdAt(),
                    Instant.now()
            );
        }
        StructureMappingProfile published = new StructureMappingProfile(
                null,
                templateVersionId,
                mappingRepository.nextVersionNo(templateVersionId),
                "PUBLISHED",
                items,
                List.of(),
                0,
                0,
                Instant.now(),
                null,
                null
        );
        return mappingRepository.save(published, currentUser);
    }

    private StructureMappingProfile defaultDraft(long templateVersionId, DocumentStructureProfile structureProfile) {
        List<StructureMappingItem> items = structureProfile.nodes().stream()
                .sorted(Comparator.comparingInt(DocumentNode::orderIndex))
                .map(node -> new StructureMappingItem(
                        node.nodeKey(),
                        normalizeRole(node.roleSuggestion()),
                        slotKeyFor(node.roleSuggestion()),
                        suggestedStatus(node.roleSuggestion()),
                        "RULE",
                        "UNKNOWN".equals(normalizeRole(node.roleSuggestion())) ? 0.3 : 0.65,
                        "",
                        node.orderIndex()
                ))
                .toList();
        return new StructureMappingProfile(
                null,
                templateVersionId,
                0,
                "DRAFT",
                items,
                List.of(),
                0,
                0,
                null,
                structureProfile.createdAt(),
                structureProfile.createdAt()
        );
    }

    private List<StructureMappingItem> normalizeAndValidateItems(
            DocumentStructureProfile structureProfile,
            List<StructureMappingItem> requestedItems
    ) {
        Map<String, DocumentNode> nodesByKey = structureProfile.nodes().stream()
                .collect(Collectors.toMap(DocumentNode::nodeKey, Function.identity()));
        return requestedItems.stream()
                .map(item -> normalizeAndValidateItem(nodesByKey, item))
                .sorted(Comparator.comparingInt(StructureMappingItem::sortOrder))
                .toList();
    }

    private StructureMappingItem normalizeAndValidateItem(Map<String, DocumentNode> nodesByKey, StructureMappingItem item) {
        if (item.nodeKey().isBlank() || !nodesByKey.containsKey(item.nodeKey())) {
            throw new StructureMappingException("STRUCTURE_MAPPING_NODE_NOT_FOUND", "Mapped node does not exist: " + item.nodeKey());
        }
        String role = item.role();
        if (!ALLOWED_ROLES.contains(role)) {
            throw new StructureMappingException("STRUCTURE_MAPPING_ROLE_INVALID", "Structure mapping role is invalid: " + item.role());
        }
        String status = item.status();
        if (!ALLOWED_ITEM_STATUSES.contains(status)) {
            throw new StructureMappingException("STRUCTURE_MAPPING_STATUS_INVALID", "Structure mapping item status is invalid: " + item.status());
        }
        String source = item.source();
        if (!ALLOWED_SOURCES.contains(source)) {
            throw new StructureMappingException("STRUCTURE_MAPPING_SOURCE_INVALID", "Structure mapping source is invalid: " + item.source());
        }
        return new StructureMappingItem(
                item.nodeKey(),
                role,
                item.slotKey().isBlank() ? slotKeyFor(role) : item.slotKey(),
                status,
                source,
                item.confidence(),
                item.notes(),
                item.sortOrder()
        );
    }

    private List<StructureMappingValidationItem> publishBlockers(
            long templateVersionId,
            List<StructureMappingItem> items,
            boolean adminOverride
    ) {
        List<StructureMappingValidationItem> blockers = new java.util.ArrayList<>();
        String documentKind = documentKind(templateVersionId);
        if (NON_TEMPLATE_DOCUMENT_KINDS.contains(documentKind) && !adminOverride) {
            blockers.add(new StructureMappingValidationItem(
                    "BLOCKING",
                    "DOCUMENT_KIND_BLOCKED",
                    "该文件类型为 " + documentKind + "，默认不允许发布为自动套版映射。",
                    null,
                    null
            ));
        }
        Set<String> confirmedRoles = items.stream()
                .filter(item -> "CONFIRMED".equals(item.status()))
                .map(StructureMappingItem::role)
                .collect(Collectors.toSet());
        REQUIRED_TEMPLATE_ROLES.stream()
                .filter(role -> !confirmedRoles.contains(role))
                .map(role -> new StructureMappingValidationItem(
                        "BLOCKING",
                        "REQUIRED_SLOT_MISSING",
                        "发布映射前必须确认 " + role + " 槽位。",
                        null,
                        role
                ))
                .forEach(blockers::add);
        return blockers;
    }

    private DocumentStructureProfile structureProfile(long templateVersionId) {
        return structureProfileRepository.findByTemplateVersionId(templateVersionId)
                .orElseThrow(() -> new StructureMappingException("DOCUMENT_STRUCTURE_PROFILE_NOT_FOUND", "Document structure profile not found"));
    }

    private CurrentUser requireTemplateAdmin() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        if (currentUser == null || !currentUser.templateAdmin()) {
            throw new StructureMappingException("STRUCTURE_MAPPING_FORBIDDEN", "Template administrator permission is required");
        }
        return currentUser;
    }

    private String documentKind(long templateVersionId) {
        return templateProfileRepository.findByTemplateVersionId(templateVersionId)
                .map(TemplateProfile::templateAnalysis)
                .map(TemplateAnalysisProfile::documentKind)
                .filter(value -> !value.isBlank())
                .orElse("UNKNOWN_DOCUMENT");
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "UNKNOWN";
        }
        return ALLOWED_ROLES.contains(role) ? role : "UNKNOWN";
    }

    private String suggestedStatus(String role) {
        String normalized = normalizeRole(role);
        return "UNKNOWN".equals(normalized) ? "NEEDS_REVIEW" : "SUGGESTED";
    }

    private String slotKeyFor(String role) {
        return switch (normalizeRole(role)) {
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
