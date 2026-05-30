package com.gongwen.assistant.exporting;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingProfile;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingRepository;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
import com.gongwen.assistant.quality.QualityCheckItem;
import com.gongwen.assistant.quality.QualityCheckRepository;
import com.gongwen.assistant.quality.QualityCheckResponse;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
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
    private static final Set<String> REQUIRED_MAPPING_ROLES = Set.of("TITLE", "BODY");
    private static final Pattern CHINESE_DATE_LINE_PATTERN = Pattern.compile("^\\d{4}\u5e74\\d{1,2}\u6708\\d{1,2}\u65e5$");

    private final DraftRepository draftRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final TemplateRepository templateRepository;
    private final TemplateProfileRepository templateProfileRepository;
    private final TemplateStructureFormattingRepository templateStructureFormattingRepository;
    private final TemplateEffectiveFormattingService templateEffectiveFormattingService;
    private final WordExportService wordExportService;
    private final QualityCheckRepository qualityCheckRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DraftNodeRepository draftNodeRepository;
    private final StructureMappingRepository structureMappingRepository;
    private final DocumentStructureProfileRepository documentStructureProfileRepository;

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
            QualityCheckRepository qualityCheckRepository,
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
                qualityCheckRepository,
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
            QualityCheckRepository qualityCheckRepository,
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
        this.qualityCheckRepository = qualityCheckRepository;
        this.currentUserProvider = currentUserProvider;
        this.draftNodeRepository = draftNodeRepository;
        this.structureMappingRepository = structureMappingRepository;
        this.documentStructureProfileRepository = documentStructureProfileRepository;
    }

    public WordExportResult exportDraft(long draftId) {
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
        ExportStructureContext structureContext = exportStructureContext(templateVersionId);
        ensureQualityCheckAllowsExport(draft.id());
        List<DraftNode> draftNodes = draftNodes(draft.id());
        Map<String, TemplateStructureFormattingProfile> structureOverrides =
                templateStructureFormattingRepository.findOverrides(templateVersionId);
        ExportFormattingContext formattingContext = exportFormattingContext(profile, structureOverrides, draftNodes);
        Map<String, String> values = draftValues(draft, draftNodes);
        ensureRequiredSlots(values, structureContext.mappingProfile());

        return wordExportService.export(readTemplateBytes(version.filePath()), WordExportRequest.draftExport(
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
                traceSnapshot(structureContext, formattingContext, draftNodes)
        ));
    }

    private void ensureQualityCheckAllowsExport(long draftId) {
        if (qualityCheckRepository == null) {
            return;
        }
        QualityCheckResponse latest = qualityCheckRepository.findLatestByDraftId(draftId)
                .orElseThrow(() -> new WordExportException(
                        "QUALITY_CHECK_REQUIRED",
                        "请先运行基础质检，通过后再导出 Word。",
                        null
                ));
        if (!latest.exportBlocked()) {
            return;
        }
        String blockingMessage = latest.items().stream()
                .filter(item -> "ERROR".equalsIgnoreCase(item.severity()))
                .findFirst()
                .map(QualityCheckItem::message)
                .orElse("基础质检存在阻断项。");
        throw new WordExportException(
                "QUALITY_CHECK_BLOCKED",
                "导出已阻断：" + blockingMessage + " 请处理后重新质检。",
                null
        );
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

    private ExportStructureContext exportStructureContext(long templateVersionId) {
        if (structureMappingRepository == null) {
            return ExportStructureContext.EMPTY;
        }
        StructureMappingProfile mapping = structureMappingRepository
                .findLatestByStatus(templateVersionId, "PUBLISHED")
                .orElseThrow(() -> new WordExportException(
                        "STRUCTURE_MAPPING_REQUIRED",
                        "发布映射前必须确认模板结构映射。",
                        null
                ));
        DocumentStructureProfile structureProfile = documentStructureProfileRepository == null
                ? null
                : documentStructureProfileRepository.findByTemplateVersionId(templateVersionId).orElse(null);
        ensureRequiredMappingRoles(mapping);
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
                .filter(candidate -> role.equals(normalizeRole(candidate.role())))
                .findFirst();
        if (node.isEmpty()) {
            return baseSlotFormatting;
        }
        TemplateStructureFormattingProfile original = structureFormatting(profile, node.get().templateNodeKey())
                .orElse(baseSlotFormatting);
        TemplateStructureFormattingProfile merged = templateEffectiveFormattingService.resolveDraftNodeFormatting(
                null,
                null,
                original,
                structureOverrides.get(node.get().templateNodeKey()),
                node.get().formatOverride()
        );
        return merged == null ? baseSlotFormatting : merged;
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
        return "BODY".equals(role) || role.startsWith("BODY_HEADING");
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
            List<DraftNode> nodes
    ) {
        StructureMappingProfile mapping = structureContext.mappingProfile();
        return new ExportTraceSnapshot(
                mapping == null ? null : mapping.mappingProfileId(),
                mapping == null ? null : mapping.versionNo(),
                structureContext.structureProfile(),
                mapping,
                formatting,
                nodeSnapshots(nodes)
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
