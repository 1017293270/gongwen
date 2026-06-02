package com.gongwen.assistant.integration;

import com.gongwen.assistant.ai.MockModelAdapter;
import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureExtractor;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.documentstructure.mapping.PublishStructureMappingRequest;
import com.gongwen.assistant.documentstructure.mapping.SaveStructureMappingRequest;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingItem;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingProfile;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingRepository;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingService;
import com.gongwen.assistant.documentstructure.semantic.DocumentSemanticSuggester;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeDto;
import com.gongwen.assistant.draft.node.DraftNodeFormatOverride;
import com.gongwen.assistant.draft.node.DraftNodeFormattingResolver;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
import com.gongwen.assistant.draft.node.DraftNodeService;
import com.gongwen.assistant.draft.node.UpdateDraftNodeRequest;
import com.gongwen.assistant.exporting.ExportRecord;
import com.gongwen.assistant.exporting.ExportRecordRepository;
import com.gongwen.assistant.exporting.WordExportResult;
import com.gongwen.assistant.exporting.WordExportService;
import com.gongwen.assistant.exporting.DraftWordExportService;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.template.TemplateIntelligenceService;
import com.gongwen.assistant.template.TemplateProperties;
import com.gongwen.assistant.template.TemplateRepository;
import com.gongwen.assistant.template.TemplateSummary;
import com.gongwen.assistant.template.TemplateUploadResponse;
import com.gongwen.assistant.template.TemplateUploadService;
import com.gongwen.assistant.template.TemplateVersion;
import com.gongwen.assistant.template.TemplateVersionRepository;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileParser;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class DocxCompleteStructurePipelineTest {
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @TempDir
    Path tempDir;

    @Test
    void speechReferenceDocumentCanBeMappedInitializedAndExportedInPlace() throws Exception {
        byte[] sourceDocx = DocxTestFactory.speechReferenceDocument();
        InMemoryTemplateVersionRepository versions = new InMemoryTemplateVersionRepository();
        InMemoryTemplateProfileRepository profiles = new InMemoryTemplateProfileRepository();
        InMemoryDocumentStructureProfileRepository structures = new InMemoryDocumentStructureProfileRepository();
        InMemoryTemplateRepository templates = new InMemoryTemplateRepository();
        TemplateUploadService uploadService = new TemplateUploadService(
                (originalFileName, fileExtension, content) -> {
                    Path target = tempDir.resolve("uploaded-" + originalFileName);
                    Files.write(target, content);
                    return target.toString();
                },
                versions,
                profiles,
                new TemplateProfileParser(),
                new TemplateProperties(tempDir.toString(), 20),
                new TemplateIntelligenceService(new MockModelAdapter()),
                templates,
                new DocumentStructureExtractor(),
                structures,
                new DocumentSemanticSuggester()
        );

        TemplateUploadResponse upload = uploadService.upload(3L, "讲话稿范文.docx", DOCX_CONTENT_TYPE, sourceDocx);
        long templateVersionId = upload.templateVersionId();
        TemplateProfile profile = profiles.findByTemplateVersionId(templateVersionId).orElseThrow();
        DocumentStructureProfile structure = structures.findByTemplateVersionId(templateVersionId).orElseThrow();

        assertThat(profile.templateAnalysis().documentKind()).isEqualTo("REFERENCE_DOCUMENT");
        assertThat(structure.extractorVersion()).isEqualTo("document-structure-v2");
        assertThat(structure.nodes()).extracting(DocumentNode::nodeType)
                .contains("PARAGRAPH", "TABLE_PARAGRAPH", "HEADER_PARAGRAPH", "FOOTER_PARAGRAPH");
        assertThat(roleByText(structure, "在全区重点工作推进会上的讲话")).isEqualTo("TITLE");
        assertThat(roleByText(structure, "同志们：")).isEqualTo("RECIPIENT");

        InMemoryStructureMappingRepository mappings = new InMemoryStructureMappingRepository();
        StructureMappingService mappingService = new StructureMappingService(
                mappings,
                structures,
                profiles,
                new FixedCurrentUserProvider()
        );
        List<StructureMappingItem> confirmedItems = confirmedEditableItems(structure);
        mappingService.saveDraft(templateVersionId, new SaveStructureMappingRequest(null, confirmedItems));
        StructureMappingProfile published = mappingService.publish(templateVersionId, new PublishStructureMappingRequest(false));

        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.items()).extracting(StructureMappingItem::role).contains("TITLE", "BODY", "RECIPIENT", "DATE");

        long draftId = 5L;
        FixedDraftRepository drafts = new FixedDraftRepository(new DraftDetailDto(
                draftId,
                "SPEECH",
                "讲话稿草稿",
                "DRAFT",
                templateVersionId,
                List.of()
        ));
        InMemoryDraftNodeRepository draftNodes = new InMemoryDraftNodeRepository();
        DraftNodeService nodeService = new DraftNodeService(
                new DraftService(drafts),
                draftNodes,
                mappings,
                structures,
                new DraftNodeFormattingResolver(
                        structures,
                        new EmptyTemplateStructureFormattingRepository(),
                        new TemplateEffectiveFormattingService()
                )
        );
        List<DraftNodeDto> initialized = nodeService.initializeNodes(draftId);
        List<String> bodyContents = initialized.stream()
                .filter(node -> "BODY".equals(node.role()))
                .map(DraftNodeDto::content)
                .toList();

        assertThat(bodyContents).hasSizeGreaterThan(3);
        assertThat(bodyContents).doesNotHaveDuplicates();
        assertThat(bodyContents).anySatisfy(content -> assertThat(content).startsWith("今天我们召开这次重点工作推进会"));

        DraftNodeDto title = firstNode(initialized, "TITLE");
        DraftNodeDto firstBody = firstNode(initialized, "BODY");
        nodeService.updateNode(draftId, title.id(), new UpdateDraftNodeRequest("T25 原位替换标题", "USER_MODIFIED_AFTER_AI"));
        nodeService.updateNode(draftId, firstBody.id(), new UpdateDraftNodeRequest("T25 原位替换正文", "USER_MODIFIED_AFTER_AI"));

        InMemoryExportRecordRepository exports = new InMemoryExportRecordRepository();
        DraftWordExportService exportService = new DraftWordExportService(
                drafts,
                versions,
                templates,
                profiles,
                new EmptyTemplateStructureFormattingRepository(),
                new TemplateEffectiveFormattingService(),
                new WordExportService(exports),
                null,
                draftNodes,
                mappings,
                structures
        );

        WordExportResult exported = exportService.exportDraft(draftId);

        assertThat(exports.savedRecord.traceSnapshot().strategy()).isEqualTo("ORIGINAL_NODE_REPLACEMENT");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(exported.content()))) {
            assertThat(document.getParagraphs()).hasSize(20);
            assertThat(document.getParagraphs().get(0).getText()).isEqualTo("T25 原位替换标题");
            assertThat(document.getParagraphs().get(4).getText()).isEqualTo("T25 原位替换正文");
            assertThat(document.getHeaderList().getFirst().getParagraphs().getFirst().getText()).isEqualTo("内部测试资料");
            assertThat(document.getTables().getFirst().getRow(0).getCell(0).getText()).contains("文档类型");
        }
    }

    @Test
    void noEditSpeechReferenceKeepsSourceNodesDraftNodesAndExportAligned() throws Exception {
        SpeechPipeline pipeline = speechPipeline(this::allSourceItems);

        List<DraftNodeDto> initialized = pipeline.nodeService().initializeNodes(pipeline.draftId());
        List<DocumentNode> sourceNodes = nonIgnoredNodes(pipeline.structure(), pipeline.published());

        assertThat(initialized).extracting(DraftNodeDto::templateNodeKey)
                .containsExactlyElementsOf(sourceNodes.stream().map(DocumentNode::nodeKey).toList());
        assertThat(initialized).extracting(DraftNodeDto::content)
                .containsExactlyElementsOf(sourceNodes.stream().map(node -> node.text().strip()).toList());
        assertThat(initialized).extracting(DraftNodeDto::role)
                .contains("UNKNOWN", "STATIC_TEXT");

        WordExportResult exported = pipeline.exportService().exportDraft(pipeline.draftId());

        assertThat(pipeline.exports().savedRecord.traceSnapshot().strategy()).isEqualTo("ORIGINAL_NODE_REPLACEMENT");
        assertThat(structuralTexts(exported.content())).containsExactlyElementsOf(structuralTexts(pipeline.sourceDocx()));
    }

    @Test
    void editedSpeechReferenceReplacesOnlyMappedEditableNodesAndKeepsStaticFacts() throws Exception {
        SpeechPipeline pipeline = speechPipeline(this::allSourceItems);
        List<DraftNodeDto> initialized = pipeline.nodeService().initializeNodes(pipeline.draftId());

        DraftNodeDto title = firstNode(initialized, "TITLE");
        DraftNodeDto firstBody = firstNode(initialized, "BODY");
        pipeline.nodeService().updateNode(pipeline.draftId(), title.id(), new UpdateDraftNodeRequest("闭环测试标题", "USER_MODIFIED_AFTER_AI"));
        pipeline.nodeService().updateNode(pipeline.draftId(), firstBody.id(), new UpdateDraftNodeRequest("闭环测试正文", "USER_MODIFIED_AFTER_AI"));

        WordExportResult exported = pipeline.exportService().exportDraft(pipeline.draftId());
        List<String> exportedTexts = structuralTexts(exported.content());

        assertThat(pipeline.exports().savedRecord.traceSnapshot().strategy()).isEqualTo("ORIGINAL_NODE_REPLACEMENT");
        assertThat(exportedTexts).contains("闭环测试标题", "闭环测试正文");
        assertThat(exportedTexts).contains(
                "政务会议讲话稿测试样例",
                "文档类型",
                "内部测试资料",
                "测试文档 | 讲话稿范文示例"
        );
        assertThat(exportedTexts).doesNotContain("在全区重点工作推进会上的讲话");
    }

    @Test
    void ignoredSpeechReferenceNodeIsSkippedFromDraftNodesAndClearedOnlyInExport() throws Exception {
        SpeechPipeline pipeline = speechPipeline(structure -> allSourceItems(structure).stream()
                .map(item -> nodeText(structure, item.nodeKey()).equals("结束语")
                        ? new StructureMappingItem(item.nodeKey(), "IGNORE", "", "CONFIRMED", "USER", 1.0d, "", item.sortOrder())
                        : item)
                .toList());

        List<DraftNodeDto> initialized = pipeline.nodeService().initializeNodes(pipeline.draftId());
        WordExportResult exported = pipeline.exportService().exportDraft(pipeline.draftId());
        List<String> exportedTexts = structuralTexts(exported.content());

        assertThat(initialized).extracting(DraftNodeDto::templateNodeKey)
                .doesNotContain(nodeKeyByText(pipeline.structure(), "结束语"));
        assertThat(exportedTexts).doesNotContain("结束语");
        assertThat(exportedTexts).contains("在全区重点工作推进会上的讲话", "我就讲这些，谢谢大家。");
        assertThat(exportedTexts).hasSize(structuralTexts(pipeline.sourceDocx()).size());
    }

    private String roleByText(DocumentStructureProfile profile, String text) {
        return profile.nodes().stream()
                .filter(node -> text.equals(node.text()))
                .findFirst()
                .orElseThrow()
                .roleSuggestion();
    }

    private SpeechPipeline speechPipeline(Function<DocumentStructureProfile, List<StructureMappingItem>> mappingFactory) throws Exception {
        byte[] sourceDocx = DocxTestFactory.speechReferenceDocument();
        InMemoryTemplateVersionRepository versions = new InMemoryTemplateVersionRepository();
        InMemoryTemplateProfileRepository profiles = new InMemoryTemplateProfileRepository();
        InMemoryDocumentStructureProfileRepository structures = new InMemoryDocumentStructureProfileRepository();
        InMemoryTemplateRepository templates = new InMemoryTemplateRepository();
        TemplateUploadService uploadService = new TemplateUploadService(
                (originalFileName, fileExtension, content) -> {
                    Path target = tempDir.resolve("closure-" + originalFileName);
                    Files.write(target, content);
                    return target.toString();
                },
                versions,
                profiles,
                new TemplateProfileParser(),
                new TemplateProperties(tempDir.toString(), 20),
                new TemplateIntelligenceService(new MockModelAdapter()),
                templates,
                new DocumentStructureExtractor(),
                structures,
                new DocumentSemanticSuggester()
        );

        TemplateUploadResponse upload = uploadService.upload(3L, "讲话稿范文.docx", DOCX_CONTENT_TYPE, sourceDocx);
        long templateVersionId = upload.templateVersionId();
        DocumentStructureProfile structure = structures.findByTemplateVersionId(templateVersionId).orElseThrow();
        InMemoryStructureMappingRepository mappings = new InMemoryStructureMappingRepository();
        StructureMappingService mappingService = new StructureMappingService(
                mappings,
                structures,
                profiles,
                new FixedCurrentUserProvider()
        );
        mappingService.saveDraft(templateVersionId, new SaveStructureMappingRequest(null, mappingFactory.apply(structure)));
        StructureMappingProfile published = mappingService.publish(templateVersionId, new PublishStructureMappingRequest(false));
        assertThat(published.status()).isEqualTo("PUBLISHED");

        long draftId = 5L;
        FixedDraftRepository drafts = new FixedDraftRepository(new DraftDetailDto(
                draftId,
                "SPEECH",
                "讲话稿草稿",
                "DRAFT",
                templateVersionId,
                List.of()
        ));
        InMemoryDraftNodeRepository draftNodes = new InMemoryDraftNodeRepository();
        DraftNodeService nodeService = new DraftNodeService(
                new DraftService(drafts),
                draftNodes,
                mappings,
                structures,
                new DraftNodeFormattingResolver(
                        structures,
                        new EmptyTemplateStructureFormattingRepository(),
                        new TemplateEffectiveFormattingService()
                )
        );
        InMemoryExportRecordRepository exports = new InMemoryExportRecordRepository();
        DraftWordExportService exportService = new DraftWordExportService(
                drafts,
                versions,
                templates,
                profiles,
                new EmptyTemplateStructureFormattingRepository(),
                new TemplateEffectiveFormattingService(),
                new WordExportService(exports),
                null,
                draftNodes,
                mappings,
                structures
        );
        return new SpeechPipeline(sourceDocx, structure, published, nodeService, exportService, exports, draftId);
    }

    private List<StructureMappingItem> allSourceItems(DocumentStructureProfile structure) {
        return structure.nodes().stream()
                .sorted(Comparator.comparingInt(DocumentNode::orderIndex))
                .map(node -> {
                    String role = node.roleSuggestion();
                    String normalizedRole = role == null || role.isBlank() ? "UNKNOWN" : role;
                    String status = "UNKNOWN".equals(normalizedRole) ? "NEEDS_REVIEW" : "CONFIRMED";
                    return new StructureMappingItem(
                            node.nodeKey(),
                            normalizedRole,
                            slotKeyFor(normalizedRole),
                            status,
                            "USER",
                            "UNKNOWN".equals(normalizedRole) ? 0.3d : 1.0d,
                            "",
                            node.orderIndex()
                    );
                })
                .toList();
    }

    private List<DocumentNode> nonIgnoredNodes(DocumentStructureProfile structure, StructureMappingProfile mapping) {
        Set<String> ignoredKeys = mapping.items().stream()
                .filter(item -> "IGNORE".equals(item.role()))
                .map(StructureMappingItem::nodeKey)
                .collect(java.util.stream.Collectors.toSet());
        return structure.nodes().stream()
                .filter(node -> !ignoredKeys.contains(node.nodeKey()))
                .sorted(Comparator.comparingInt(DocumentNode::orderIndex))
                .toList();
    }

    private String nodeText(DocumentStructureProfile structure, String nodeKey) {
        return structure.nodes().stream()
                .filter(node -> node.nodeKey().equals(nodeKey))
                .findFirst()
                .orElseThrow()
                .text();
    }

    private String nodeKeyByText(DocumentStructureProfile structure, String text) {
        return structure.nodes().stream()
                .filter(node -> text.equals(node.text()))
                .findFirst()
                .orElseThrow()
                .nodeKey();
    }

    private List<String> structuralTexts(byte[] docxBytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            List<String> texts = new ArrayList<>();
            document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText().strip())
                    .forEach(texts::add);
            document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells().forEach(cell ->
                    cell.getParagraphs().stream()
                            .map(paragraph -> paragraph.getText().strip())
                            .forEach(texts::add)
            )));
            document.getHeaderList().forEach(header -> header.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText().strip())
                    .forEach(texts::add));
            document.getFooterList().forEach(footer -> footer.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText().strip())
                    .forEach(texts::add));
            return texts;
        }
    }

    private List<StructureMappingItem> confirmedEditableItems(DocumentStructureProfile structure) {
        Set<String> mappedRoles = Set.of(
                "TITLE",
                "RECIPIENT",
                "BODY",
                "BODY_HEADING_LEVEL_1",
                "BODY_HEADING_LEVEL_2",
                "BODY_HEADING_LEVEL_3",
                "ATTACHMENT_NOTE",
                "SIGNATURE",
                "DATE"
        );
        return structure.nodes().stream()
                .filter(node -> mappedRoles.contains(node.roleSuggestion()))
                .map(node -> new StructureMappingItem(
                        node.nodeKey(),
                        node.roleSuggestion(),
                        slotKeyFor(node.roleSuggestion()),
                        "CONFIRMED",
                        "USER",
                        1.0d,
                        "",
                        node.orderIndex()
                ))
                .toList();
    }

    private String slotKeyFor(String role) {
        return switch (role) {
            case "TITLE" -> "title";
            case "RECIPIENT" -> "recipient";
            case "BODY", "BODY_HEADING_LEVEL_1", "BODY_HEADING_LEVEL_2", "BODY_HEADING_LEVEL_3" -> "body";
            case "ATTACHMENT_NOTE" -> "attachment";
            case "SIGNATURE" -> "signature";
            case "DATE" -> "date";
            default -> "";
        };
    }

    private DraftNodeDto firstNode(List<DraftNodeDto> nodes, String role) {
        return nodes.stream()
                .filter(node -> role.equals(node.role()))
                .findFirst()
                .orElseThrow();
    }

    private record SpeechPipeline(
            byte[] sourceDocx,
            DocumentStructureProfile structure,
            StructureMappingProfile published,
            DraftNodeService nodeService,
            DraftWordExportService exportService,
            InMemoryExportRecordRepository exports,
            long draftId
    ) {
    }

    private static final class FixedCurrentUserProvider extends CurrentUserProvider {
        @Override
        public CurrentUser currentUser() {
            return new CurrentUser(1L, "admin", "管理员", 1L, "办公室", List.of("TEMPLATE_ADMIN"));
        }
    }

    private static final class InMemoryTemplateVersionRepository implements TemplateVersionRepository {
        private final Map<Long, TemplateVersion> versions = new HashMap<>();
        private long nextId = 1;

        @Override
        public TemplateVersion create(long templateId, String originalFileName, String contentType, long fileSizeBytes, String filePath) {
            long id = nextId++;
            TemplateVersion version = new TemplateVersion(
                    id,
                    templateId,
                    nextVersionNo(templateId),
                    originalFileName,
                    contentType,
                    fileSizeBytes,
                    filePath,
                    null,
                    "PENDING",
                    null,
                    null,
                    Instant.now()
            );
            versions.put(id, version);
            return version;
        }

        @Override
        public int nextVersionNo(long templateId) {
            return (int) versions.values().stream()
                    .filter(version -> version.templateId() == templateId)
                    .count() + 1;
        }

        @Override
        public Optional<TemplateVersion> findById(long id) {
            return Optional.ofNullable(versions.get(id));
        }

        @Override
        public void markParsed(long id, String profileHash) {
            TemplateVersion current = versions.get(id);
            versions.put(id, new TemplateVersion(
                    current.id(),
                    current.templateId(),
                    current.versionNo(),
                    current.originalFileName(),
                    current.contentType(),
                    current.fileSizeBytes(),
                    current.filePath(),
                    profileHash,
                    "READY",
                    null,
                    null,
                    current.createdAt()
            ));
        }

        @Override
        public void markFailed(long id, String errorCode, String errorMessage) {
            TemplateVersion current = versions.get(id);
            versions.put(id, new TemplateVersion(
                    current.id(),
                    current.templateId(),
                    current.versionNo(),
                    current.originalFileName(),
                    current.contentType(),
                    current.fileSizeBytes(),
                    current.filePath(),
                    null,
                    "FAILED",
                    errorCode,
                    errorMessage,
                    current.createdAt()
            ));
        }
    }

    private static final class InMemoryTemplateProfileRepository implements TemplateProfileRepository {
        private final Map<Long, TemplateProfile> profiles = new HashMap<>();

        @Override
        public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
            profiles.put(templateVersionId, profile);
        }

        @Override
        public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.ofNullable(profiles.get(templateVersionId));
        }
    }

    private static final class InMemoryDocumentStructureProfileRepository implements DocumentStructureProfileRepository {
        private final Map<Long, DocumentStructureProfile> profiles = new HashMap<>();

        @Override
        public void save(long templateVersionId, DocumentStructureProfile profile) {
            profiles.put(templateVersionId, profile);
        }

        @Override
        public Optional<DocumentStructureProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.ofNullable(profiles.get(templateVersionId));
        }
    }

    private static final class InMemoryTemplateRepository implements TemplateRepository {
        @Override
        public TemplateSummary create(String templateName, String documentTypeCode) {
            return new TemplateSummary(3L, templateName, documentTypeCode, "ACTIVE");
        }

        @Override
        public List<TemplateSummary> findAll(String documentTypeCode) {
            return List.of();
        }

        @Override
        public Optional<TemplateSummary> findById(long id) {
            return Optional.of(new TemplateSummary(id, "讲话稿范文", "SPEECH", "ACTIVE"));
        }
    }

    private static final class InMemoryStructureMappingRepository implements StructureMappingRepository {
        private final List<StructureMappingProfile> profiles = new ArrayList<>();
        private long nextId = 100;

        @Override
        public StructureMappingProfile save(StructureMappingProfile profile, CurrentUser currentUser) {
            StructureMappingProfile saved = new StructureMappingProfile(
                    profile.mappingProfileId() == null ? nextId++ : profile.mappingProfileId(),
                    profile.templateVersionId(),
                    profile.versionNo(),
                    profile.status(),
                    profile.items(),
                    profile.validationItems(),
                    profile.confirmedCount(),
                    profile.needsReviewCount(),
                    profile.publishedAt(),
                    profile.createdAt() == null ? Instant.now() : profile.createdAt(),
                    Instant.now()
            );
            profiles.add(saved);
            return saved;
        }

        @Override
        public Optional<StructureMappingProfile> findLatest(long templateVersionId) {
            return profiles.stream()
                    .filter(profile -> profile.templateVersionId() == templateVersionId)
                    .max(Comparator.comparing(StructureMappingProfile::updatedAt));
        }

        @Override
        public Optional<StructureMappingProfile> findLatestByStatus(long templateVersionId, String status) {
            return profiles.stream()
                    .filter(profile -> profile.templateVersionId() == templateVersionId)
                    .filter(profile -> status.equals(profile.status()))
                    .max(Comparator.comparing(StructureMappingProfile::updatedAt));
        }

        @Override
        public int nextVersionNo(long templateVersionId) {
            return (int) profiles.stream()
                    .filter(profile -> profile.templateVersionId() == templateVersionId)
                    .count() + 1;
        }
    }

    private record FixedDraftRepository(DraftDetailDto draft) implements DraftRepository {
        @Override
        public DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks) {
            return draft;
        }

        @Override
        public DraftDetailDto findById(long id) {
            return draft;
        }

        @Override
        public DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks) {
            return draft;
        }
    }

    private static final class InMemoryDraftNodeRepository implements DraftNodeRepository {
        private final Map<Long, List<DraftNode>> byDraft = new HashMap<>();
        private long nextId = 200;

        @Override
        public List<DraftNode> findByDraftId(long draftId) {
            return byDraft.getOrDefault(draftId, List.of()).stream()
                    .sorted(Comparator.comparingInt(DraftNode::sortOrder).thenComparingLong(DraftNode::id))
                    .toList();
        }

        @Override
        public boolean existsByDraftId(long draftId) {
            return !findByDraftId(draftId).isEmpty();
        }

        @Override
        public List<DraftNode> replaceForDraft(long draftId, List<DraftNode> nodes) {
            List<DraftNode> saved = nodes.stream()
                    .map(node -> new DraftNode(
                            nextId++,
                            draftId,
                            node.structureMappingProfileId(),
                            node.templateNodeKey(),
                            node.parentTemplateNodeKey(),
                            node.nodeType(),
                            node.role(),
                            node.slotKey(),
                            node.title(),
                            node.content(),
                            node.sortOrder(),
                            node.status(),
                            node.formatOverride(),
                            Instant.now(),
                            Instant.now()
                    ))
                    .toList();
            byDraft.put(draftId, saved);
            return saved;
        }

        @Override
        public Optional<DraftNode> updateContent(long draftId, long nodeId, String content, String status) {
            List<DraftNode> updated = findByDraftId(draftId).stream()
                    .map(node -> node.id() == nodeId
                            ? new DraftNode(
                                    node.id(),
                                    node.draftId(),
                                    node.structureMappingProfileId(),
                                    node.templateNodeKey(),
                                    node.parentTemplateNodeKey(),
                                    node.nodeType(),
                                    node.role(),
                                    node.slotKey(),
                                    node.title(),
                                    content,
                                    node.sortOrder(),
                                    status,
                                    node.formatOverride(),
                                    node.createdAt(),
                                    Instant.now()
                            )
                            : node)
                    .toList();
            byDraft.put(draftId, updated);
            return updated.stream().filter(node -> node.id() == nodeId).findFirst();
        }

        @Override
        public Optional<DraftNode> updateFormatOverride(long draftId, long nodeId, DraftNodeFormatOverride override, String status) {
            throw new UnsupportedOperationException("format override is not used in this test");
        }
    }

    private static final class EmptyTemplateStructureFormattingRepository implements TemplateStructureFormattingRepository {
        @Override
        public Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId) {
            return Map.of();
        }

        @Override
        public void saveOverride(long templateVersionId, String structureKey, TemplateStructureFormattingProfile formatting) {
            throw new UnsupportedOperationException("save override is not used in this test");
        }
    }

    private static final class InMemoryExportRecordRepository implements ExportRecordRepository {
        private ExportRecord savedRecord;

        @Override
        public void save(ExportRecord record) {
            this.savedRecord = record;
        }
    }
}
