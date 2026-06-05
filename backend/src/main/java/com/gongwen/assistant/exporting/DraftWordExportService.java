package com.gongwen.assistant.exporting;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.documentstructure.DocumentHeadingRoleDetector;
import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingItem;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingProfile;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingRepository;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import com.gongwen.assistant.exporting.word.DocxNodeReplacementRenderer;
import com.gongwen.assistant.exporting.word.ExportFormattingContext;
import com.gongwen.assistant.template.TemplateRepository;
import com.gongwen.assistant.template.TemplateSummary;
import com.gongwen.assistant.template.TemplateVersion;
import com.gongwen.assistant.template.TemplateVersionRepository;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import com.gongwen.assistant.template.profile.TemplateStructureProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DraftWordExportService {
    private static final String PLACEHOLDER_TITLE = "\u6807\u9898";
    private static final String PLACEHOLDER_RECIPIENT = "\u4e3b\u9001";
    private static final String PLACEHOLDER_ATTACHMENT = "\u9644\u4ef6";
    private static final String PLACEHOLDER_SIGNATURE = "\u843d\u6b3e";
    private static final String PLACEHOLDER_DATE = "\u65e5\u671f";
    private static final String PLACEHOLDER_BODY = "\u6b63\u6587";
    private static final Set<String> DISALLOWED_DOCUMENT_KINDS =
            Set.of("MANUAL_OR_GUIDE", "POLICY_OR_REGULATION", "ORDINARY_DOCUMENT");
    private static final Set<String> ORIGINAL_NODE_REPLACEMENT_KINDS =
            Set.of("REFERENCE_DOCUMENT", "OFFICIAL_DOCUMENT");
    private static final Set<String> NON_REPLACEABLE_ORIGINAL_ROLES =
            Set.of("UNKNOWN", "STATIC_TEXT", "IGNORE", "HEADER", "FOOTER", "TABLE_ATTACHMENT");
    private static final Set<String> REQUIRED_MAPPING_ROLES = Set.of("TITLE", "BODY");
    private static final Pattern CHINESE_DATE_LINE_PATTERN = Pattern.compile("^\\d{4}\u5e74\\d{1,2}\u6708\\d{1,2}\u65e5$");
    private static final String STRATEGY_PLACEHOLDER_REPLACEMENT = "PLACEHOLDER_REPLACEMENT";
    private static final String STRATEGY_ORIGINAL_NODE_REPLACEMENT = "ORIGINAL_NODE_REPLACEMENT";
    private static final String STRATEGY_GENERATED_SNAPSHOT = "GENERATED_SNAPSHOT";

    private final DraftRepository draftRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateRepository templateRepository;
    private final TemplateProfileRepository templateProfileRepository;
    private final TemplateStructureFormattingRepository templateStructureFormattingRepository;
    private final TemplateEffectiveFormattingService templateEffectiveFormattingService;
    private final WordExportService wordExportService;
    private final CurrentUserProvider currentUserProvider;
    private final DraftNodeRepository draftNodeRepository;
    private final StructureMappingRepository structureMappingRepository;
    private final DocumentStructureProfileRepository documentStructureProfileRepository;
    private final DocxNodeReplacementRenderer nodeReplacementRenderer;

    public DraftWordExportService(
            DraftRepository draftRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateRepository templateRepository,
            TemplateProfileRepository templateProfileRepository,
            TemplateStructureFormattingRepository templateStructureFormattingRepository,
            TemplateEffectiveFormattingService templateEffectiveFormattingService,
            WordExportService wordExportService
    ) {
        this(
                draftRepository,
                templateVersionRepository,
                templateRepository,
                templateProfileRepository,
                templateStructureFormattingRepository,
                templateEffectiveFormattingService,
                wordExportService,
                null,
                null,
                null,
                null
        );
    }

    public DraftWordExportService(
            DraftRepository draftRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateRepository templateRepository,
            TemplateProfileRepository templateProfileRepository,
            TemplateStructureFormattingRepository templateStructureFormattingRepository,
            TemplateEffectiveFormattingService templateEffectiveFormattingService,
            WordExportService wordExportService,
            CurrentUserProvider currentUserProvider
    ) {
        this(
                draftRepository,
                templateVersionRepository,
                templateRepository,
                templateProfileRepository,
                templateStructureFormattingRepository,
                templateEffectiveFormattingService,
                wordExportService,
                currentUserProvider,
                null,
                null,
                null
        );
    }

    @Autowired
    public DraftWordExportService(
            DraftRepository draftRepository,
            TemplateVersionRepository templateVersionRepository,
            TemplateRepository templateRepository,
            TemplateProfileRepository templateProfileRepository,
            TemplateStructureFormattingRepository templateStructureFormattingRepository,
            TemplateEffectiveFormattingService templateEffectiveFormattingService,
            WordExportService wordExportService,
            CurrentUserProvider currentUserProvider,
            DraftNodeRepository draftNodeRepository,
            StructureMappingRepository structureMappingRepository,
            DocumentStructureProfileRepository documentStructureProfileRepository
    ) {
        this.draftRepository = draftRepository;
        this.templateVersionRepository = templateVersionRepository;
        this.templateRepository = templateRepository;
        this.templateProfileRepository = templateProfileRepository;
        this.templateStructureFormattingRepository = templateStructureFormattingRepository;
        this.templateEffectiveFormattingService = templateEffectiveFormattingService;
        this.wordExportService = wordExportService;
        this.currentUserProvider = currentUserProvider;
        this.draftNodeRepository = draftNodeRepository;
        this.structureMappingRepository = structureMappingRepository;
        this.documentStructureProfileRepository = documentStructureProfileRepository;
        this.nodeReplacementRenderer = new DocxNodeReplacementRenderer();
    }

    public WordExportResult exportDraft(long draftId) {
        DraftExportPlan plan = draftExportPlan(draftId, true);
        if (STRATEGY_ORIGINAL_NODE_REPLACEMENT.equals(plan.strategy())) {
            return exportOriginalNodeReplacement(
                    plan.templateBytes(),
                    plan.request(),
                    plan.structureContext(),
                    plan.draftNodes(),
                    plan.structureOverrides()
            );
        }
        return wordExportService.export(plan.templateBytes(), plan.request());
    }

    public DraftWordRenderResult renderDraftForPreview(long draftId) {
        DraftExportPlan plan = draftExportPlan(draftId, false);
        WordExportResult result;
        if (STRATEGY_ORIGINAL_NODE_REPLACEMENT.equals(plan.strategy())) {
            result = renderOriginalNodeReplacementForPreview(
                    plan.templateBytes(),
                    plan.request(),
                    plan.structureContext(),
                    plan.draftNodes(),
                    plan.structureOverrides()
            );
        } else {
            result = wordExportService.render(plan.templateBytes(), plan.request());
        }
        return new DraftWordRenderResult(plan.templateVersionId(), result.fileName(), result.content());
    }

    private DraftExportPlan draftExportPlan(long draftId, boolean requirePublishedMapping) {
        CurrentUser currentUser = currentUserOrNull();
        DraftDetailDto draft = draftRepository.findById(draftId, currentUser);
        Long templateVersionId = draft.templateVersionId();
        if (templateVersionId == null) {
            throw new WordExportException(
                    "TEMPLATE_VERSION_REQUIRED",
                    "\u8bf7\u5148\u9009\u62e9\u5957\u7248\u6a21\u677f\u540e\u518d\u5bfc\u51fa Word",
                    null
            );
        }

        TemplateVersion version = templateVersionRepository.findById(templateVersionId)
                .orElseThrow(() -> new WordExportException(
                        "TEMPLATE_VERSION_NOT_FOUND",
                        "\u6240\u9009\u6a21\u677f\u7248\u672c\u4e0d\u5b58\u5728",
                        null
                ));
        if (!"READY".equals(version.parseStatus())) {
            throw new WordExportException(
                    "TEMPLATE_VERSION_NOT_READY",
                    "\u6240\u9009\u6a21\u677f\u7248\u672c\u5c1a\u672a\u89e3\u6790\u5b8c\u6210\uff0c\u4e0d\u80fd\u5bfc\u51fa",
                    null
            );
        }

        TemplateSummary template = templateRepository.findById(version.templateId())
                .orElse(new TemplateSummary(version.templateId(), "\u516c\u6587\u6a21\u677f", draft.documentTypeCode(), "ACTIVE"));

        TemplateProfile profile = templateProfile(templateVersionId);
        ensureDocumentKindAllowsExport(profile);
        ExportStructureContext structureContext = exportStructureContext(templateVersionId, requirePublishedMapping);
        List<DraftNode> draftNodes = draftNodes(draft.id());
        Map<String, TemplateStructureFormattingProfile> structureOverrides =
                templateStructureFormattingRepository.findOverrides(templateVersionId);
        ExportFormattingContext formattingContext = exportFormattingContext(profile, structureOverrides, draftNodes);
        Map<String, String> values = draftValues(draft, draftNodes);
        ensureRequiredSlots(values, structureContext.mappingProfile());
        byte[] templateBytes = readTemplateBytes(version.filePath());
        String strategy = exportStrategy(templateBytes, profile, structureContext.mappingProfile(), draftNodes);
        WordExportRequest request = WordExportRequest.draftExport(
                template.templateName(),
                version.versionNo(),
                version.templateId(),
                version.id(),
                draft.id(),
                currentUser == null ? null : currentUser.id(),
                currentUser == null ? null : currentUser.departmentId(),
                values,
                formattingContext,
                profile,
                traceSnapshot(structureContext, formattingContext, draftNodes, strategy)
        );

        return new DraftExportPlan(
                templateVersionId,
                templateBytes,
                request,
                structureContext,
                draftNodes,
                structureOverrides,
                strategy
        );
    }

    private WordExportResult exportOriginalNodeReplacement(
            byte[] templateBytes,
            WordExportRequest request,
            ExportStructureContext structureContext,
            List<DraftNode> draftNodes,
            Map<String, TemplateStructureFormattingProfile> structureOverrides
    ) {
        try {
            StructureMappingProfile mapping = structureContext == null ? null : structureContext.mappingProfile();
            byte[] rendered = nodeReplacementRenderer.render(
                    templateBytes,
                    originalNodeReplacements(draftNodes),
                    ignoredNodeKeys(mapping, draftNodes),
                    deletedNodeKeys(draftNodes, structureContext),
                    originalNodeInsertions(draftNodes, structureContext, request.templateProfile(), structureOverrides)
            );
            return wordExportService.exportRendered(request, rendered);
        } catch (DocxNodeReplacementRenderer.MissingNodeLocatorException exception) {
            throw new WordExportException(
                    "EXPORT_NODE_LOCATOR_MISSING",
                    "\u5bfc\u51fa\u8282\u70b9\u5b9a\u4f4d\u5931\u8d25\uff1a" + exception.nodeKey(),
                    exception
            );
        }
    }

    private WordExportResult renderOriginalNodeReplacementForPreview(
            byte[] templateBytes,
            WordExportRequest request,
            ExportStructureContext structureContext,
            List<DraftNode> draftNodes,
            Map<String, TemplateStructureFormattingProfile> structureOverrides
    ) {
        try {
            StructureMappingProfile mapping = structureContext == null ? null : structureContext.mappingProfile();
            byte[] rendered = nodeReplacementRenderer.render(
                    templateBytes,
                    originalNodeReplacements(draftNodes),
                    ignoredNodeKeys(mapping, draftNodes),
                    deletedNodeKeys(draftNodes, structureContext),
                    originalNodeInsertions(draftNodes, structureContext, request.templateProfile(), structureOverrides)
            );
            return wordExportService.renderRendered(request, rendered);
        } catch (DocxNodeReplacementRenderer.MissingNodeLocatorException exception) {
            throw new WordExportException(
                    "EXPORT_NODE_LOCATOR_MISSING",
                    "\u5bfc\u51fa\u8282\u70b9\u5b9a\u4f4d\u5931\u8d25\uff1a" + exception.nodeKey(),
                    exception
            );
        }
    }

    private String exportStrategy(
            byte[] templateBytes,
            TemplateProfile profile,
            StructureMappingProfile mapping,
            List<DraftNode> draftNodes
    ) {
        if (wordExportService.hasPlaceholders(templateBytes)) {
            return STRATEGY_PLACEHOLDER_REPLACEMENT;
        }
        if (isOriginalNodeReplacementSupported(profile, mapping, draftNodes)) {
            return STRATEGY_ORIGINAL_NODE_REPLACEMENT;
        }
        return STRATEGY_GENERATED_SNAPSHOT;
    }

    private boolean isOriginalNodeReplacementSupported(
            TemplateProfile profile,
            StructureMappingProfile mapping,
            List<DraftNode> draftNodes
    ) {
        return mapping != null
                && ORIGINAL_NODE_REPLACEMENT_KINDS.contains(documentKind(profile))
                && !originalNodeReplacements(draftNodes).isEmpty();
    }

    private String documentKind(TemplateProfile profile) {
        if (profile == null || profile.templateAnalysis() == null) {
            return "";
        }
        return profile.templateAnalysis().documentKind();
    }

    private CurrentUser currentUserOrNull() {
        return currentUserProvider == null ? null : currentUserProvider.currentUser();
    }

    private TemplateProfile templateProfile(long templateVersionId) {
        return templateProfileRepository.findByTemplateVersionId(templateVersionId)
                .orElseThrow(() -> new WordExportException(
                        "TEMPLATE_PROFILE_NOT_FOUND",
                        "\u6240\u9009\u6a21\u677f\u7248\u672c\u7f3a\u5c11\u89e3\u6790\u6863\u6848\uff0c\u8bf7\u91cd\u65b0\u89e3\u6790\u6216\u4e0a\u4f20\u6a21\u677f",
                        null
                ));
    }

    private void ensureDocumentKindAllowsExport(TemplateProfile profile) {
        if (profile == null || profile.templateAnalysis() == null) {
            return;
        }
        String documentKind = profile.templateAnalysis().documentKind();
        if (!DISALLOWED_DOCUMENT_KINDS.contains(documentKind)) {
            return;
        }
        String warning = profile.templateAnalysis().blockingWarnings().stream()
                .findFirst()
                .orElse("该文件类型不允许直接自动套版导出。");
        throw new WordExportException(
                "DOCUMENT_KIND_EXPORT_BLOCKED",
                "导出已阻断：" + warning,
                null
        );
    }

    private ExportStructureContext exportStructureContext(long templateVersionId, boolean requirePublishedMapping) {
        if (structureMappingRepository == null) {
            return ExportStructureContext.EMPTY;
        }
        Optional<StructureMappingProfile> publishedMapping = structureMappingRepository
                .findLatestByStatus(templateVersionId, "PUBLISHED");
        if (publishedMapping.isEmpty() && requirePublishedMapping) {
            throw new WordExportException(
                    "STRUCTURE_MAPPING_REQUIRED",
                    "导出前需要当前模板版本已有已发布的结构映射。请在模板解析工作台保存草稿并发布映射后，再回到工作台重建结构并导出。",
                    null
            );
        }
        StructureMappingProfile mapping = publishedMapping.orElse(null);
        DocumentStructureProfile structureProfile = documentStructureProfileRepository == null
                ? null
                : documentStructureProfileRepository.findByTemplateVersionId(templateVersionId).orElse(null);
        if (mapping != null) {
            ensureRequiredMappingRoles(mapping);
        }
        return new ExportStructureContext(structureProfile, mapping);
    }

    private void ensureRequiredMappingRoles(StructureMappingProfile mapping) {
        Set<String> confirmedRoles = mapping.items().stream()
                .filter(item -> "CONFIRMED".equalsIgnoreCase(item.status()))
                .map(item -> normalizeRole(item.role()))
                .collect(Collectors.toSet());
        List<String> missing = REQUIRED_MAPPING_ROLES.stream()
                .filter(role -> !confirmedRoles.contains(role))
                .sorted()
                .toList();
        if (!missing.isEmpty()) {
            throw new WordExportException(
                    "STRUCTURE_MAPPING_REQUIRED_SLOT_MISSING",
                    "发布映射缺少必需结构槽位：" + String.join(", ", missing),
                    null
            );
        }
    }

    private List<DraftNode> draftNodes(long draftId) {
        if (draftNodeRepository == null) {
            return List.of();
        }
        return draftNodeRepository.findByDraftId(draftId).stream()
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .toList();
    }

    private ExportFormattingContext exportFormattingContext(
            TemplateProfile profile,
            Map<String, TemplateStructureFormattingProfile> structureOverrides,
            List<DraftNode> draftNodes
    ) {
        Map<String, TemplateStructureFormattingProfile> safeOverrides =
                structureOverrides == null ? Map.of() : structureOverrides;
        ExportFormattingContext base = templateEffectiveFormattingService.resolve(profile, safeOverrides);
        if (draftNodes == null || draftNodes.isEmpty()) {
            return base;
        }
        return new ExportFormattingContext(
                nodeFormatting(profile, safeOverrides, base.title(), draftNodes, "TITLE"),
                nodeFormatting(profile, safeOverrides, base.recipient(), draftNodes, "RECIPIENT"),
                nodeFormatting(profile, safeOverrides, base.body(), draftNodes, "BODY"),
                nodeFormatting(profile, safeOverrides, base.signature(), draftNodes, "SIGNATURE"),
                nodeFormatting(profile, safeOverrides, base.date(), draftNodes, "DATE")
        );
    }

    private TemplateStructureFormattingProfile nodeFormatting(
            TemplateProfile profile,
            Map<String, TemplateStructureFormattingProfile> structureOverrides,
            TemplateStructureFormattingProfile baseSlotFormatting,
            List<DraftNode> draftNodes,
            String role
    ) {
        Optional<DraftNode> node = draftNodes.stream()
                .filter(candidate -> !isDeletedDraftNode(candidate))
                .filter(candidate -> role.equals(normalizeRole(candidate.role())))
                .findFirst();
        if (node.isEmpty()) {
            return baseSlotFormatting;
        }
        String sourceKey = formattingSourceKey(node.get());
        TemplateStructureFormattingProfile original = structureFormatting(profile, sourceKey)
                .orElse(baseSlotFormatting);
        TemplateStructureFormattingProfile merged = templateEffectiveFormattingService.resolveDraftNodeFormatting(
                null,
                null,
                original,
                structureOverrides.get(sourceKey),
                node.get().formatOverride()
        );
        return merged == null ? baseSlotFormatting : merged;
    }

    private String formattingSourceKey(DraftNode node) {
        if (isSyntheticDraftNode(node) && !isBlank(node.metadata().styleSourceNodeKey())) {
            return node.metadata().styleSourceNodeKey();
        }
        return node.templateNodeKey();
    }

    private Optional<TemplateStructureFormattingProfile> structureFormatting(TemplateProfile profile, String nodeKey) {
        if (profile == null || nodeKey == null || nodeKey.isBlank()) {
            return Optional.empty();
        }
        return profile.structures().stream()
                .filter(structure -> nodeKey.equals(structure.structureKey()))
                .findFirst()
                .map(TemplateStructureProfile::formatting);
    }

    private byte[] readTemplateBytes(String filePath) {
        try {
            return Files.readAllBytes(Path.of(filePath));
        } catch (IOException exception) {
            throw new WordExportException(
                    "TEMPLATE_FILE_UNAVAILABLE",
                    "\u6a21\u677f\u6587\u4ef6\u4e0d\u53ef\u8bfb\u53d6\uff0c\u8bf7\u91cd\u65b0\u4e0a\u4f20\u6a21\u677f",
                    exception
            );
        }
    }

    private Map<String, String> draftValues(DraftDetailDto draft, List<DraftNode> nodes) {
        if (nodes != null && !nodes.isEmpty()) {
            return draftNodeValues(draft, nodes);
        }
        return draftValues(draft);
    }

    private Map<String, String> draftValues(DraftDetailDto draft) {
        Map<String, String> values = new HashMap<>();
        String title = blockValue(draft, "TITLE", draft.title());
        String recipient = blockValue(draft, "RECIPIENT", "");
        String attachment = blockValue(draft, "ATTACHMENT", "");
        String signature = blockValue(draft, "SIGNATURE", "");
        String date = blockValue(draft, "DATE", "");

        List<String> bodyLines = draft.blocks().stream()
                .filter(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .sorted(Comparator.comparingInt(DraftBlockDto::sortOrder))
                .map(DraftBlockDto::content)
                .filter(content -> content != null && !content.isBlank())
                .flatMap(content -> content.lines())
                .toList();
        CleanedBody cleanedBody = cleanBodyLines(bodyLines, recipient, attachment, signature, date);

        putValue(values, "TITLE", PLACEHOLDER_TITLE, title);
        putValue(values, "RECIPIENT", PLACEHOLDER_RECIPIENT, firstNonBlank(recipient, cleanedBody.recipient()));
        putValue(values, "ATTACHMENT", PLACEHOLDER_ATTACHMENT, firstNonBlank(attachment, cleanedBody.attachment()));
        putValue(values, "SIGNATURE", PLACEHOLDER_SIGNATURE, firstNonBlank(signature, cleanedBody.signature()));
        putValue(values, "DATE", PLACEHOLDER_DATE, firstNonBlank(date, cleanedBody.date()));
        putValue(values, "BODY_PARAGRAPH", PLACEHOLDER_BODY, cleanedBody.body());
        return values;
    }

    private Map<String, String> draftNodeValues(DraftDetailDto draft, List<DraftNode> nodes) {
        Map<String, String> values = new HashMap<>();
        List<DraftNode> sortedNodes = nodes.stream()
                .filter(node -> !isDeletedDraftNode(node))
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .toList();
        String title = firstNodeValue(sortedNodes, "TITLE", draft.title());
        String recipient = firstNodeValue(sortedNodes, "RECIPIENT", "");
        String attachment = joinedNodeValues(sortedNodes, Set.of("ATTACHMENT_NOTE", "ATTACHMENT"));
        String signature = firstNodeValue(sortedNodes, "SIGNATURE", "");
        String date = firstNodeValue(sortedNodes, "DATE", "");
        List<String> bodyLines = sortedNodes.stream()
                .filter(this::isBodyNode)
                .map(DraftNode::content)
                .filter(content -> content != null && !content.isBlank())
                .flatMap(content -> content.lines())
                .toList();
        CleanedBody cleanedBody = cleanBodyLines(bodyLines, recipient, attachment, signature, date);

        putValue(values, "TITLE", PLACEHOLDER_TITLE, title);
        putValue(values, "RECIPIENT", PLACEHOLDER_RECIPIENT, firstNonBlank(recipient, cleanedBody.recipient()));
        putValue(values, "ATTACHMENT", PLACEHOLDER_ATTACHMENT, firstNonBlank(attachment, cleanedBody.attachment()));
        putValue(values, "SIGNATURE", PLACEHOLDER_SIGNATURE, firstNonBlank(signature, cleanedBody.signature()));
        putValue(values, "DATE", PLACEHOLDER_DATE, firstNonBlank(date, cleanedBody.date()));
        putValue(values, "BODY_PARAGRAPH", PLACEHOLDER_BODY, cleanedBody.body());
        return values;
    }

    private Map<String, String> originalNodeReplacements(List<DraftNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> replacements = new LinkedHashMap<>();
        nodes.stream()
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .filter(node -> !isDeletedDraftNode(node))
                .filter(node -> !isBlank(node.templateNodeKey()))
                .filter(node -> !isSyntheticDraftNode(node))
                .filter(this::isReplaceableOriginalNode)
                .forEach(node -> replacements.put(node.templateNodeKey(), node.content() == null ? "" : node.content()));
        return replacements;
    }

    private List<DocxNodeReplacementRenderer.NodeInsertion> originalNodeInsertions(
            List<DraftNode> nodes,
            ExportStructureContext structureContext,
            TemplateProfile profile,
            Map<String, TemplateStructureFormattingProfile> structureOverrides
    ) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        String fallbackAnchorNodeKey = fallbackOriginalNodeInsertionAnchor(nodes, structureContext);
        return nodes.stream()
                .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                .filter(node -> !isDeletedDraftNode(node))
                .filter(this::isSyntheticDraftNode)
                .filter(this::isReplaceableOriginalNode)
                .map(node -> {
                    String styleSourceNodeKey = insertionStyleSourceNodeKey(node, nodes, structureContext);
                    return new DocxNodeReplacementRenderer.NodeInsertion(
                            firstNonBlank(node.metadata().anchorTemplateNodeKey(), fallbackAnchorNodeKey),
                            insertionPositionForExport(node.metadata().insertPosition()),
                            node.content() == null ? "" : node.content(),
                            styleSourceNodeKey,
                            insertionFormatting(node, styleSourceNodeKey, profile, structureContext, structureOverrides)
                    );
                })
                .filter(insertion -> !isBlank(insertion.anchorNodeKey()))
                .toList();
    }

    private TemplateStructureFormattingProfile insertionFormatting(
            DraftNode node,
            String styleSourceNodeKey,
            TemplateProfile profile,
            ExportStructureContext structureContext,
            Map<String, TemplateStructureFormattingProfile> structureOverrides
    ) {
        if (isBlank(styleSourceNodeKey)) {
            return null;
        }
        TemplateStructureFormattingProfile original = documentStructureFormatting(structureContext, styleSourceNodeKey)
                .or(() -> structureFormatting(profile, styleSourceNodeKey))
                .orElse(null);
        TemplateStructureFormattingProfile structureOverride =
                structureOverrides == null ? null : structureOverrides.get(styleSourceNodeKey);
        return templateEffectiveFormattingService.resolveDraftNodeFormatting(
                null,
                null,
                original,
                structureOverride,
                node.formatOverride()
        );
    }

    private Optional<TemplateStructureFormattingProfile> documentStructureFormatting(
            ExportStructureContext structureContext,
            String nodeKey
    ) {
        DocumentStructureProfile structureProfile = structureContext == null ? null : structureContext.structureProfile();
        if (structureProfile == null || structureProfile.nodes() == null || isBlank(nodeKey)) {
            return Optional.empty();
        }
        return structureProfile.nodes().stream()
                .filter(node -> nodeKey.equals(node.nodeKey()))
                .findFirst()
                .map(DocumentNode::formatting);
    }

    private String insertionStyleSourceNodeKey(
            DraftNode node,
            List<DraftNode> nodes,
            ExportStructureContext structureContext
    ) {
        String explicitSourceKey = node.metadata().styleSourceNodeKey();
        if (!isBlank(explicitSourceKey)) {
            return normalizedExplicitStyleSourceNodeKey(node, explicitSourceKey, nodes, structureContext);
        }
        String sourceKeyFromNodes = styleSourceNodeKeyForRole(nodes, node.role());
        if (!isBlank(sourceKeyFromNodes)) {
            return sourceKeyFromNodes;
        }
        String sourceKeyFromStructure = styleSourceNodeKeyForRole(structureContext, node.role());
        if (!isBlank(sourceKeyFromStructure)) {
            return sourceKeyFromStructure;
        }
        StructureMappingProfile mapping = structureContext == null ? null : structureContext.mappingProfile();
        return styleSourceNodeKeyForRole(mapping, node.role());
    }

    private String normalizedExplicitStyleSourceNodeKey(
            DraftNode node,
            String explicitSourceKey,
            List<DraftNode> nodes,
            ExportStructureContext structureContext
    ) {
        String nodeRole = normalizeRole(node.role());
        if (DocumentHeadingRoleDetector.isHeadingRole(nodeRole)) {
            String sourceTextRole = firstNonBlank(
                    sourceHeadingRoleFromStructureProfile(structureContext, explicitSourceKey),
                    sourceHeadingRoleFromDraftNodes(nodes, explicitSourceKey)
            );
            if (nodeRole.equals(sourceTextRole)) {
                return explicitSourceKey;
            }
            if (!isBlank(sourceTextRole) && !nodeRole.equals(sourceTextRole)) {
                return bestStyleSourceNodeKey(node, nodes, structureContext);
            }
        }
        String sourceRole = firstNonBlank(
                sourceRoleFromStructureProfile(structureContext, explicitSourceKey),
                sourceRoleFromDraftNodes(nodes, explicitSourceKey)
        );
        if (DocumentHeadingRoleDetector.isHeadingRole(nodeRole)
                && DocumentHeadingRoleDetector.isHeadingRole(sourceRole)
                && !nodeRole.equals(sourceRole)) {
            return bestStyleSourceNodeKey(node, nodes, structureContext);
        }
        return explicitSourceKey;
    }

    private String bestStyleSourceNodeKey(DraftNode node, List<DraftNode> nodes, ExportStructureContext structureContext) {
        String sourceKeyFromNodes = styleSourceNodeKeyForRole(nodes, node.role());
        if (!isBlank(sourceKeyFromNodes)) {
            return sourceKeyFromNodes;
        }
        String sourceKeyFromStructure = styleSourceNodeKeyForRole(structureContext, node.role());
        if (!isBlank(sourceKeyFromStructure)) {
            return sourceKeyFromStructure;
        }
        StructureMappingProfile mapping = structureContext == null ? null : structureContext.mappingProfile();
        String sourceKeyFromMapping = styleSourceNodeKeyForRole(mapping, node.role());
        return isBlank(sourceKeyFromMapping) ? "" : sourceKeyFromMapping;
    }

    private String sourceHeadingRoleFromStructureProfile(ExportStructureContext structureContext, String nodeKey) {
        if (structureContext == null || structureContext.structureProfile() == null || isBlank(nodeKey)) {
            return "";
        }
        return structureContext.structureProfile().nodes().stream()
                .filter(node -> nodeKey.equals(node.nodeKey()))
                .findFirst()
                .map(DocumentNode::text)
                .map(DocumentHeadingRoleDetector::detect)
                .orElse("");
    }

    private String sourceHeadingRoleFromDraftNodes(List<DraftNode> nodes, String nodeKey) {
        if (nodes == null || nodes.isEmpty() || isBlank(nodeKey)) {
            return "";
        }
        return nodes.stream()
                .filter(node -> !isSyntheticDraftNode(node))
                .filter(node -> nodeKey.equals(node.templateNodeKey()))
                .findFirst()
                .map(node -> DocumentHeadingRoleDetector.detect(firstNonBlank(node.content(), node.title())))
                .orElse("");
    }

    private String sourceRoleFromStructureProfile(ExportStructureContext structureContext, String nodeKey) {
        if (structureContext == null || structureContext.structureProfile() == null || isBlank(nodeKey)) {
            return "";
        }
        return structureContext.structureProfile().nodes().stream()
                .filter(node -> nodeKey.equals(node.nodeKey()))
                .findFirst()
                .map(DocumentNode::roleSuggestion)
                .map(this::normalizeRole)
                .orElse("");
    }

    private String sourceRoleFromDraftNodes(List<DraftNode> nodes, String nodeKey) {
        if (nodes == null || nodes.isEmpty() || isBlank(nodeKey)) {
            return "";
        }
        return nodes.stream()
                .filter(node -> !isSyntheticDraftNode(node))
                .filter(node -> nodeKey.equals(node.templateNodeKey()))
                .findFirst()
                .map(DraftNode::role)
                .map(this::normalizeRole)
                .orElse("");
    }

    private Set<String> ignoredNodeKeys(StructureMappingProfile mapping, List<DraftNode> draftNodes) {
        return mapping == null
                ? Set.of()
                : mapping.items().stream()
                        .filter(item -> "IGNORE".equals(normalizeRole(item.role())))
                        .map(item -> item.nodeKey())
                        .filter(nodeKey -> !isBlank(nodeKey))
                        .collect(Collectors.toSet());
    }

    private Set<String> deletedNodeKeys(List<DraftNode> draftNodes, ExportStructureContext structureContext) {
        if (draftNodes == null) {
            return Set.of();
        }
        Set<String> deletedKeys = draftNodes.stream()
                .filter(this::isDeletedDraftNode)
                .filter(node -> !isSyntheticDraftNode(node))
                .map(DraftNode::templateNodeKey)
                .filter(nodeKey -> !isBlank(nodeKey))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (hasOutlineSyntheticBodyStructureNodes(draftNodes)) {
            deletedKeys.addAll(bodyRegionOriginalNodeKeys(structureContext, draftNodes));
        }
        return deletedKeys;
    }

    private String fallbackOriginalNodeInsertionAnchor(List<DraftNode> draftNodes, ExportStructureContext structureContext) {
        if (!hasOutlineSyntheticBodyStructureNodes(draftNodes)) {
            return "";
        }
        return draftNodes.stream()
                .filter(this::isDeletedDraftNode)
                .filter(node -> !isSyntheticDraftNode(node))
                .filter(node -> isBodyStructureRole(node.role()))
                .map(DraftNode::templateNodeKey)
                .filter(nodeKey -> !isBlank(nodeKey))
                .findFirst()
                .orElseGet(() -> bodyRegionOriginalNodeKeys(structureContext, draftNodes).stream().findFirst().orElse(""));
    }

    private boolean hasOutlineSyntheticBodyStructureNodes(List<DraftNode> draftNodes) {
        if (draftNodes == null || draftNodes.isEmpty()) {
            return false;
        }
        return draftNodes.stream()
                .filter(node -> !isDeletedDraftNode(node))
                .filter(this::isOutlineSyntheticDraftNode)
                .filter(node -> isBodyStructureRole(node.role()))
                .findAny()
                .isPresent();
    }

    private Set<String> bodyRegionOriginalNodeKeys(ExportStructureContext structureContext, List<DraftNode> draftNodes) {
        StructureMappingProfile mapping = structureContext == null ? null : structureContext.mappingProfile();
        LinkedHashSet<String> keys = new LinkedHashSet<>(bodyStructureMappingNodeKeys(mapping));
        if (draftNodes != null) {
            draftNodes.stream()
                    .filter(this::isDeletedDraftNode)
                    .filter(node -> !isSyntheticDraftNode(node))
                    .filter(node -> isBodyStructureRole(node.role()))
                    .map(DraftNode::templateNodeKey)
                    .filter(nodeKey -> !isBlank(nodeKey))
                    .forEach(keys::add);
        }
        DocumentStructureProfile structureProfile = structureContext == null ? null : structureContext.structureProfile();
        if (structureProfile == null || structureProfile.nodes() == null || structureProfile.nodes().isEmpty()) {
            return keys;
        }
        Map<String, String> rolesByNodeKey = mapping == null || mapping.items() == null
                ? Map.of()
                : mapping.items().stream()
                        .collect(Collectors.toMap(
                                StructureMappingItem::nodeKey,
                                StructureMappingItem::role,
                                (left, right) -> right
                        ));
        List<DocumentNode> orderedNodes = structureProfile.nodes().stream()
                .filter(node -> node != null && !isBlank(node.nodeKey()))
                .sorted(Comparator.comparingInt(DocumentNode::orderIndex))
                .toList();
        int bodyStartIndex = firstBodyRegionIndex(orderedNodes, rolesByNodeKey, keys);
        if (bodyStartIndex < 0) {
            return keys;
        }
        for (int index = bodyStartIndex; index < orderedNodes.size(); index++) {
            DocumentNode node = orderedNodes.get(index);
            String role = effectiveOriginalNodeRole(node, rolesByNodeKey);
            if (index > bodyStartIndex && isBodyRegionBoundary(node, role)) {
                break;
            }
            if (isBodyRegionRemovableNode(node, role)) {
                keys.add(node.nodeKey());
            }
        }
        return keys;
    }

    private int firstBodyRegionIndex(List<DocumentNode> orderedNodes, Map<String, String> rolesByNodeKey, Set<String> knownBodyKeys) {
        for (int index = 0; index < orderedNodes.size(); index++) {
            DocumentNode node = orderedNodes.get(index);
            String role = effectiveOriginalNodeRole(node, rolesByNodeKey);
            if (knownBodyKeys.contains(node.nodeKey()) || isBodyStructureRole(role)) {
                return index;
            }
        }
        return -1;
    }

    private boolean isBodyRegionRemovableNode(DocumentNode node, String role) {
        return "PARAGRAPH".equalsIgnoreCase(node.nodeType())
                && !isBodyRegionBoundary(node, role);
    }

    private boolean isBodyRegionBoundary(DocumentNode node, String role) {
        if (node == null) {
            return false;
        }
        String nodeType = node.nodeType() == null ? "" : node.nodeType().strip().toUpperCase();
        if ("HEADER_PARAGRAPH".equals(nodeType) || "FOOTER_PARAGRAPH".equals(nodeType) || "TABLE_PARAGRAPH".equals(nodeType)) {
            return true;
        }
        String normalizedRole = normalizeRole(role);
        return Set.of(
                "TITLE",
                "SUBTITLE",
                "RECIPIENT",
                "ISSUING_ORGAN",
                "DOC_NUMBER",
                "ATTACHMENT_NOTE",
                "ATTACHMENT_CONTENT",
                "ATTACHMENT",
                "TABLE_ATTACHMENT",
                "SIGNATURE",
                "DATE",
                "CC"
        ).contains(normalizedRole);
    }

    private String effectiveOriginalNodeRole(DocumentNode node, Map<String, String> rolesByNodeKey) {
        String mappedRole = rolesByNodeKey.getOrDefault(node.nodeKey(), "");
        return isBlank(mappedRole) || "UNKNOWN".equals(normalizeRole(mappedRole))
                ? node.roleSuggestion()
                : mappedRole;
    }

    private Set<String> bodyStructureMappingNodeKeys(StructureMappingProfile mapping) {
        if (mapping == null || mapping.items() == null) {
            return Set.of();
        }
        return mapping.items().stream()
                .filter(item -> isBodyStructureRole(item.role()))
                .map(item -> item.nodeKey())
                .filter(nodeKey -> !isBlank(nodeKey))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String styleSourceNodeKeyForRole(List<DraftNode> nodes, String role) {
        String normalizedRole = normalizeRole(role);
        if (DocumentHeadingRoleDetector.isHeadingRole(normalizedRole)) {
            String sourceByNumbering = headingStyleSourceNodeKeyByNumbering(nodes, normalizedRole);
            if (!isBlank(sourceByNumbering)) {
                return sourceByNumbering;
            }
            String exact = exactStyleSourceNodeKey(nodes, normalizedRole);
            if (!isBlank(exact) && !hasConflictingHeadingNumbering(nodes, exact, normalizedRole)) {
                return exact;
            }
            return exactStyleSourceNodeKey(nodes, "BODY");
        }
        String exact = exactStyleSourceNodeKey(nodes, role);
        if (!isBlank(exact)) {
            return exact;
        }
        return "";
    }

    private String styleSourceNodeKeyForRole(ExportStructureContext structureContext, String role) {
        String normalizedRole = normalizeRole(role);
        if (structureContext == null
                || structureContext.structureProfile() == null
                || !DocumentHeadingRoleDetector.isHeadingRole(normalizedRole)) {
            return "";
        }
        String sourceByNumbering = structureContext.structureProfile().nodes().stream()
                .filter(node -> normalizedRole.equals(DocumentHeadingRoleDetector.detect(node.text())))
                .map(DocumentNode::nodeKey)
                .filter(nodeKey -> !isBlank(nodeKey))
                .findFirst()
                .orElse("");
        if (!isBlank(sourceByNumbering)) {
            return sourceByNumbering;
        }
        String exact = structureContext.structureProfile().nodes().stream()
                .filter(node -> normalizedRole.equals(normalizeRole(node.roleSuggestion())))
                .filter(node -> DocumentHeadingRoleDetector.detect(node.text()).isBlank())
                .map(DocumentNode::nodeKey)
                .filter(nodeKey -> !isBlank(nodeKey))
                .findFirst()
                .orElse("");
        if (!isBlank(exact)) {
            return exact;
        }
        return structureContext.structureProfile().nodes().stream()
                .filter(node -> "BODY".equals(normalizeRole(node.roleSuggestion())))
                .map(DocumentNode::nodeKey)
                .filter(nodeKey -> !isBlank(nodeKey))
                .findFirst()
                .orElse("");
    }

    private String styleSourceNodeKeyForRole(StructureMappingProfile mapping, String role) {
        String exact = exactStyleSourceNodeKey(mapping, role);
        if (!isBlank(exact)) {
            return exact;
        }
        if (normalizeRole(role).startsWith("BODY_HEADING_LEVEL_")) {
            return exactStyleSourceNodeKey(mapping, "BODY");
        }
        return "";
    }

    private String exactStyleSourceNodeKey(List<DraftNode> nodes, String role) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        String normalizedRole = normalizeRole(role);
        return nodes.stream()
                .filter(node -> !isSyntheticDraftNode(node))
                .filter(node -> normalizedRole.equals(normalizeRole(node.role())))
                .map(DraftNode::templateNodeKey)
                .filter(nodeKey -> !isBlank(nodeKey))
                .findFirst()
                .orElse("");
    }

    private String headingStyleSourceNodeKeyByNumbering(List<DraftNode> nodes, String role) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        return nodes.stream()
                .filter(node -> !isSyntheticDraftNode(node))
                .filter(node -> role.equals(DocumentHeadingRoleDetector.detect(firstNonBlank(node.content(), node.title()))))
                .map(DraftNode::templateNodeKey)
                .filter(nodeKey -> !isBlank(nodeKey))
                .findFirst()
                .orElse("");
    }

    private boolean hasConflictingHeadingNumbering(List<DraftNode> nodes, String sourceNodeKey, String expectedRole) {
        if (nodes == null || nodes.isEmpty() || isBlank(sourceNodeKey)) {
            return false;
        }
        String detectedRole = nodes.stream()
                .filter(node -> sourceNodeKey.equals(node.templateNodeKey()))
                .map(node -> DocumentHeadingRoleDetector.detect(firstNonBlank(node.content(), node.title())))
                .filter(role -> !role.isBlank())
                .findFirst()
                .orElse("");
        return !isBlank(detectedRole) && !detectedRole.equals(expectedRole);
    }

    private String exactStyleSourceNodeKey(StructureMappingProfile mapping, String role) {
        if (mapping == null || mapping.items() == null || mapping.items().isEmpty()) {
            return "";
        }
        String normalizedRole = normalizeRole(role);
        return mapping.items().stream()
                .filter(item -> normalizedRole.equals(normalizeRole(item.role())))
                .map(StructureMappingItem::nodeKey)
                .filter(nodeKey -> !isBlank(nodeKey))
                .findFirst()
                .orElse("");
    }

    private boolean isBodyStructureRole(String role) {
        String normalizedRole = normalizeRole(role);
        return "BODY".equals(normalizedRole) || normalizedRole.startsWith("BODY_HEADING");
    }

    private boolean isDeletedDraftNode(DraftNode node) {
        return node != null && "DELETED".equalsIgnoreCase(node.status());
    }

    private boolean isSyntheticDraftNode(DraftNode node) {
        return node != null && node.metadata() != null && node.metadata().synthetic();
    }

    private boolean isOutlineSyntheticDraftNode(DraftNode node) {
        return isSyntheticDraftNode(node) && node.templateNodeKey().startsWith("outline-");
    }

    private String insertionPositionForExport(String position) {
        return "BEFORE".equalsIgnoreCase(position) ? "BEFORE" : "AFTER";
    }

    private boolean isReplaceableOriginalRole(String role) {
        String normalizedRole = normalizeRole(role);
        return !normalizedRole.isBlank() && !NON_REPLACEABLE_ORIGINAL_ROLES.contains(normalizedRole);
    }

    private boolean isReplaceableOriginalNode(DraftNode node) {
        if (node == null || !isReplaceableOriginalRole(node.role())) {
            return false;
        }
        String role = normalizeRole(node.role());
        return !"BODY".equals(role) || isBodyNode(node);
    }

    private String firstNodeValue(List<DraftNode> nodes, String role, String fallback) {
        return nodes.stream()
                .filter(node -> role.equals(normalizeRole(node.role())))
                .map(DraftNode::content)
                .filter(content -> content != null && !content.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private String joinedNodeValues(List<DraftNode> nodes, Set<String> roles) {
        return nodes.stream()
                .filter(node -> roles.contains(normalizeRole(node.role())))
                .map(DraftNode::content)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private boolean isBodyNode(DraftNode node) {
        String role = normalizeRole(node.role());
        if (role.startsWith("BODY_HEADING")) {
            return true;
        }
        return "BODY".equals(role) && looksLikeBodyContent(node.content());
    }

    private boolean looksLikeBodyContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isBlank()) {
            return false;
        }
        String compact = normalized.replaceAll("\\s+", "");
        if (compact.startsWith("附件") || compact.startsWith("联系人") || compact.startsWith("（联系人") || compact.startsWith("(联系人")) {
            return false;
        }
        if (compact.contains("印发") || compact.contains("抄送")) {
            return false;
        }
        if (compact.length() <= 32 && compact.matches("^[0-9Xx]{2,4}年[0-9Xx]{1,2}月[0-9Xx]{1,2}日$")) {
            return false;
        }
        return true;
    }

    private void ensureRequiredSlots(Map<String, String> values, StructureMappingProfile mapping) {
        if (mapping == null) {
            return;
        }
        List<String> emptySlots = REQUIRED_MAPPING_ROLES.stream()
                .filter(role -> isBlank(valueForRequiredRole(values, role)))
                .sorted()
                .toList();
        if (!emptySlots.isEmpty()) {
            throw new WordExportException(
                    "EXPORT_REQUIRED_SLOT_EMPTY",
                    "导出必填结构槽位为空：" + String.join(", ", emptySlots),
                    null
            );
        }
    }

    private String valueForRequiredRole(Map<String, String> values, String role) {
        if ("TITLE".equals(role)) {
            return values.get("TITLE");
        }
        if ("BODY".equals(role)) {
            return values.get("BODY_PARAGRAPH");
        }
        return values.get(role);
    }

    private ExportTraceSnapshot traceSnapshot(
            ExportStructureContext structureContext,
            ExportFormattingContext formatting,
            List<DraftNode> nodes,
            String strategy
    ) {
        StructureMappingProfile mapping = structureContext.mappingProfile();
        return new ExportTraceSnapshot(
                mapping == null ? null : mapping.mappingProfileId(),
                mapping == null ? null : mapping.versionNo(),
                structureContext.structureProfile(),
                mapping,
                formatting,
                nodeSnapshots(nodes),
                strategy
        );
    }

    private List<ExportNodeSnapshot> nodeSnapshots(List<DraftNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .map(node -> new ExportNodeSnapshot(
                        node.id(),
                        node.templateNodeKey(),
                        node.role(),
                        node.slotKey(),
                        node.status(),
                        node.content() == null ? 0 : node.content().length(),
                        !isBlank(node.content()),
                        node.formatOverride()
                ))
                .toList();
    }

    private String blockValue(DraftDetailDto draft, String blockType, String fallback) {
        return draft.blocks().stream()
                .filter(block -> blockType.equals(block.blockType()))
                .findFirst()
                .map(DraftBlockDto::content)
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback);
    }

    private CleanedBody cleanBodyLines(
            List<String> bodyLines,
            String explicitRecipient,
            String explicitAttachment,
            String explicitSignature,
            String explicitDate
    ) {
        List<String> cleanedLines = new ArrayList<>();
        String recipient = "";
        String attachment = "";
        for (String line : bodyLines) {
            String normalized = line.strip();
            if (normalized.isBlank()) {
                cleanedLines.add(line);
                continue;
            }
            if (isRecipientLine(normalized)) {
                if (isBlank(explicitRecipient) && recipient.isBlank()) {
                    recipient = stripTrailingColon(normalized);
                }
                continue;
            }
            if (isAttachmentLine(normalized)) {
                if (isBlank(explicitAttachment) && attachment.isBlank()) {
                    attachment = normalized;
                }
                continue;
            }
            cleanedLines.add(line);
        }

        String signature = "";
        String date = "";
        int dateIndex = lastNonBlankIndex(cleanedLines);
        if (dateIndex >= 0 && isDateLine(cleanedLines.get(dateIndex).strip())) {
            int signatureIndex = previousNonBlankIndex(cleanedLines, dateIndex);
            if (signatureIndex >= 0 && isLikelySignatureLine(cleanedLines.get(signatureIndex).strip())) {
                if (isBlank(explicitDate)) {
                    date = cleanedLines.get(dateIndex).strip();
                }
                if (isBlank(explicitSignature)) {
                    signature = cleanedLines.get(signatureIndex).strip();
                }
                cleanedLines.remove(dateIndex);
                cleanedLines.remove(signatureIndex);
            }
        }

        String body = cleanedLines.stream()
                .collect(Collectors.joining("\n"))
                .strip();
        return new CleanedBody(body, recipient, attachment, signature, date);
    }

    private boolean isRecipientLine(String line) {
        return line.length() <= 80
                && (line.endsWith("\uff1a") || line.endsWith(":"))
                && !line.contains("\uff0c")
                && !line.contains(",")
                && !line.contains("\u3002")
                && !line.contains("\uff1b")
                && !line.contains(";")
                && (line.contains("\u90e8\u95e8") || line.contains("\u5355\u4f4d") || line.contains("\u673a\u5173"));
    }

    private boolean isAttachmentLine(String line) {
        return line.startsWith("\u9644\u4ef6\uff1a") || line.startsWith("\u9644\u4ef6:");
    }

    private boolean isDateLine(String line) {
        return CHINESE_DATE_LINE_PATTERN.matcher(line).matches();
    }

    private boolean isLikelySignatureLine(String line) {
        return line.length() <= 40
                && !isRecipientLine(line)
                && !isAttachmentLine(line)
                && !isDateLine(line);
    }

    private int lastNonBlankIndex(List<String> lines) {
        return previousNonBlankIndex(lines, lines.size());
    }

    private int previousNonBlankIndex(List<String> lines, int beforeIndex) {
        for (int index = beforeIndex - 1; index >= 0; index--) {
            if (!lines.get(index).isBlank()) {
                return index;
            }
        }
        return -1;
    }

    private String stripTrailingColon(String value) {
        return value.replaceAll("[:\uff1a]+$", "");
    }

    private String firstNonBlank(String preferred, String fallback) {
        return isBlank(preferred) ? fallback : preferred;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeRole(String role) {
        return role == null || role.isBlank() ? "UNKNOWN" : role.strip().toUpperCase();
    }

    private void putValue(Map<String, String> values, String blockType, String placeholder, String content) {
        String normalizedContent = content == null ? "" : content;
        values.put(placeholder, normalizedContent);
        values.put(blockType, normalizedContent);
    }

    private record CleanedBody(String body, String recipient, String attachment, String signature, String date) {
    }

    private record ExportStructureContext(
            DocumentStructureProfile structureProfile,
            StructureMappingProfile mappingProfile
    ) {
        private static final ExportStructureContext EMPTY = new ExportStructureContext(null, null);
    }

    private record DraftExportPlan(
            long templateVersionId,
            byte[] templateBytes,
            WordExportRequest request,
            ExportStructureContext structureContext,
            List<DraftNode> draftNodes,
            Map<String, TemplateStructureFormattingProfile> structureOverrides,
            String strategy
    ) {
    }

    private record ExportNodeSnapshot(
            long nodeId,
            String templateNodeKey,
            String role,
            String slotKey,
            String status,
            int contentLength,
            boolean hasContent,
            Object formatOverride
    ) {
    }
}
