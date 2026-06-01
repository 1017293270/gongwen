package com.gongwen.assistant.exporting;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingItem;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingProfile;
import com.gongwen.assistant.documentstructure.mapping.StructureMappingRepository;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeFormatOverride;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
import com.gongwen.assistant.exporting.word.ExportFormattingContext;
import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.template.TemplateRepository;
import com.gongwen.assistant.template.TemplateSummary;
import com.gongwen.assistant.template.TemplateVersion;
import com.gongwen.assistant.template.TemplateVersionRepository;
import com.gongwen.assistant.template.profile.TemplateAnalysisProfile;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateLineSpacingProfile;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileParser;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import com.gongwen.assistant.template.profile.TemplateStructureProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftWordExportServiceTest {
    private static final String PLACEHOLDER_TITLE = "{{\u6807\u9898}}";
    private static final String PLACEHOLDER_RECIPIENT = "{{\u4e3b\u9001}}";
    private static final String PLACEHOLDER_BODY = "{{\u6b63\u6587}}";
    private static final String PLACEHOLDER_ATTACHMENT = "{{\u9644\u4ef6}}";
    private static final String PLACEHOLDER_SIGNATURE = "{{\u843d\u6b3e}}";
    private static final String PLACEHOLDER_DATE = "{{\u65e5\u671f}}";

    @TempDir
    Path tempDir;

    @Test
    void exportsBoundDraftWithSelectedTemplateVersion() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(
                PLACEHOLDER_TITLE,
                PLACEHOLDER_RECIPIENT,
                PLACEHOLDER_BODY,
                PLACEHOLDER_ATTACHMENT,
                PLACEHOLDER_SIGNATURE,
                PLACEHOLDER_DATE
        );
        Path templatePath = tempDir.resolve("notice-template.docx");
        Files.write(templatePath, templateBytes);

        InMemoryExportRecordRepository records = new InMemoryExportRecordRepository();
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(1L, 9L, "Test notice")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(emptyProfile()),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(records)
        );

        WordExportResult result = service.exportDraft(1L);

        String text = DocxTestFactory.readText(result.content());
        assertThat(result.fileName()).isEqualTo("Test Template-v2.docx");
        assertThat(text).contains("Year-end archive notice");
        assertThat(text).contains("All departments");
        assertThat(text).contains("1. Meeting time");
        assertThat(text).contains("Headquarters conference room");
        assertThat(text).contains("General Office");
        assertThat(records.savedStatus).isEqualTo("SUCCESS");
    }

    @Test
    void exportsCurrentDraftContentWhenTemplateHasNoPlaceholders() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(
                "Reference template heading",
                "This template body should not leak into the exported draft."
        );
        Path templatePath = tempDir.resolve("reference-template.docx");
        Files.write(templatePath, templateBytes);

        InMemoryExportRecordRepository records = new InMemoryExportRecordRepository();
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(1L, 9L, "Snapshot title")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(emptyProfile()),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(records)
        );

        WordExportResult result = service.exportDraft(1L);

        String text = DocxTestFactory.readText(result.content());
        assertThat(text).contains("Year-end archive notice");
        assertThat(text).contains("All departments");
        assertThat(text).contains("1. Meeting time");
        assertThat(text).contains("2026-05-07 09:30");
        assertThat(text).contains("2. Meeting location");
        assertThat(text).contains("Headquarters conference room");
        assertThat(text).contains("General Office");
        assertThat(text).doesNotContain("This template body should not leak into the exported draft.");
        assertThat(records.savedStatus).isEqualTo("SUCCESS");
    }

    @Test
    void cleansSemanticLinesFromContaminatedBodyBlocksBeforeExporting() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithNoticeReferenceSkeleton();
        Path templatePath = tempDir.resolve("reference-template.docx");
        Files.write(templatePath, templateBytes);
        TemplateProfile profile = new TemplateProfileParser().parse(templateBytes);
        CapturingWordExportService wordExportService = new CapturingWordExportService();

        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(contaminatedDraft(1L, 9L)),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(profile),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                wordExportService
        );

        service.exportDraft(1L);

        assertThat(wordExportService.lastRequest.templateProfile()).isSameAs(profile);
        assertThat(wordExportService.lastRequest.values().get("主送")).isEqualTo("各部门、各直属单位");
        assertThat(wordExportService.lastRequest.values().get("附件")).isEqualTo("附件：会议议题征集表");
        assertThat(wordExportService.lastRequest.values().get("落款")).isEqualTo("办公室");
        assertThat(wordExportService.lastRequest.values().get("日期")).isEqualTo("2026年5月30日");
        assertThat(wordExportService.lastRequest.values().get("正文"))
                .contains("为统筹推进近期重点工作")
                .doesNotContain("各部门、各直属单位：", "附件：会议议题征集表", "示例单位办公室", "2026年5月27日");
    }

    @Test
    void failsWhenDraftHasNoTemplateVersion() {
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(new DraftDetailDto(
                        1L,
                        "NOTICE",
                        "No template",
                        "DRAFT",
                        null,
                        List.of(new DraftBlockDto(1L, "TITLE", "No template", 10))
                )),
                new FixedTemplateVersionRepository("missing.docx"),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(null),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(new InMemoryExportRecordRepository())
        );

        assertThatThrownBy(() -> service.exportDraft(1L))
                .isInstanceOf(WordExportException.class)
                .satisfies(error ->
                        assertThat(((WordExportException) error).errorCode()).isEqualTo("TEMPLATE_VERSION_REQUIRED"));
    }

    @Test
    void failsWhenReadyTemplateVersionHasNoStoredProfile() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(PLACEHOLDER_TITLE);
        Path templatePath = tempDir.resolve("missing-profile-template.docx");
        Files.write(templatePath, templateBytes);

        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(5L, 19L, "Missing profile")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(null),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(new InMemoryExportRecordRepository())
        );

        assertThatThrownBy(() -> service.exportDraft(5L))
                .isInstanceOf(WordExportException.class)
                .satisfies(error ->
                        assertThat(((WordExportException) error).errorCode()).isEqualTo("TEMPLATE_PROFILE_NOT_FOUND"));
    }

    @Test
    void exportDraftBuildsFormattingContextFromBoundTemplateVersionId() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(PLACEHOLDER_TITLE, PLACEHOLDER_BODY);
        Path templatePath = tempDir.resolve("formatting-template.docx");
        Files.write(templatePath, templateBytes);
        long templateVersionId = 9L;

        TemplateProfile profile = new TemplateProfile(
                1,
                List.of(
                        new TemplateStructureProfile(
                                "title-1",
                                "TITLE",
                                "Title",
                                "Title preview",
                                "PARAGRAPH",
                                null,
                                null,
                                "PROFILE",
                                new TemplateStructureFormattingProfile("FangSong", 44, true, "CENTER", 0, 0, 0, 240)
                        ),
                        new TemplateStructureProfile(
                                "body-1",
                                "BODY",
                                "Body",
                                "Body preview",
                                "PARAGRAPH",
                                null,
                                null,
                                "PROFILE",
                                new TemplateStructureFormattingProfile(null, 32, false, "LEFT", 420, 360, 0, 0)
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        CapturingTemplateProfileRepository profileRepository = new CapturingTemplateProfileRepository(profile);
        CapturingTemplateStructureFormattingRepository formattingRepository =
                new CapturingTemplateStructureFormattingRepository(Map.of(
                        "body-1",
                        new TemplateStructureFormattingProfile("KaiTi", null, null, null, 560, null, null, null)
                ));

        CapturingWordExportService wordExportService = new CapturingWordExportService();
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(11L, templateVersionId, "Formatting title")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                profileRepository,
                formattingRepository,
                new TemplateEffectiveFormattingService(),
                wordExportService
        );

        service.exportDraft(11L);

        assertThat(wordExportService.lastRequest).isNotNull();
        assertThat(profileRepository.lastRequestedTemplateVersionId).isEqualTo(templateVersionId);
        assertThat(formattingRepository.lastRequestedTemplateVersionId).isEqualTo(templateVersionId);
        ExportFormattingContext formatting = wordExportService.lastRequest.formatting();
        assertThat(formatting).isNotNull();
        assertThat(formatting).isNotEqualTo(ExportFormattingContext.EMPTY);
        assertThat(formatting.title()).isNotNull();
        assertThat(formatting.title().alignment()).isEqualTo("CENTER");
        assertThat(formatting.title().fontFamily()).isEqualTo("FangSong");
        assertThat(formatting.body()).isNotNull();
        assertThat(formatting.body().indentationFirstLine()).isEqualTo(560);
        assertThat(formatting.body().fontFamily()).isEqualTo("KaiTi");
        assertThat(formatting.body().fontSizeHalfPoints()).isEqualTo(32);
        assertThat(formatting.body().alignment()).isEqualTo("LEFT");
    }

    @Test
    void exportsFromDraftNodesAndMergesDraftNodeFormattingWithoutMutatingTemplateDefaults() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(PLACEHOLDER_TITLE, PLACEHOLDER_BODY);
        Path templatePath = tempDir.resolve("node-export-template.docx");
        Files.write(templatePath, templateBytes);
        long draftId = 31L;
        long templateVersionId = 9L;
        long mappingProfileId = 55L;
        TemplateStructureFormattingProfile templateBodyFormatting = new TemplateStructureFormattingProfile(
                "FangSong",
                32,
                false,
                "LEFT",
                420,
                360,
                0,
                0,
                null,
                "FangSong",
                "Times New Roman",
                new TemplateLineSpacingProfile("AUTO", null, 180)
        );
        TemplateProfile profile = new TemplateProfile(
                1,
                List.of(
                        structure("title-1", "TITLE", "CENTER", 0, 0),
                        new TemplateStructureProfile(
                                "body-1",
                                "BODY",
                                "Body",
                                "Body preview",
                                "PARAGRAPH",
                                null,
                                null,
                                "PROFILE",
                                templateBodyFormatting
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        StructureMappingProfile mapping = publishedMapping(templateVersionId, mappingProfileId,
                mappingItem("title-1", "TITLE", "TITLE", 10),
                mappingItem("body-1", "BODY", "BODY_PARAGRAPH", 20)
        );
        List<DraftNode> nodes = List.of(
                draftNode(101L, draftId, mappingProfileId, "title-1", "TITLE", "TITLE", "节点标题", 10, DraftNodeFormatOverride.empty()),
                draftNode(
                        102L,
                        draftId,
                        mappingProfileId,
                        "body-1",
                        "BODY",
                        "BODY_PARAGRAPH",
                        "节点正文第一段",
                        20,
                        new DraftNodeFormatOverride("KaiTi", "Arial", 18.0, null, null, 560, "AUTO", 150, null, null)
                )
        );
        CapturingWordExportService wordExportService = new CapturingWordExportService();
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(draftId, templateVersionId, "Legacy draft")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(profile),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                wordExportService,
                null,
                new FixedDraftNodeRepository(nodes),
                new FixedStructureMappingRepository(mapping),
                new FixedDocumentStructureProfileRepository(documentStructureProfile("title-1", "body-1"))
        );

        service.exportDraft(draftId);

        assertThat(wordExportService.lastRequest.values().get("标题")).isEqualTo("节点标题");
        assertThat(wordExportService.lastRequest.values().get("正文"))
                .contains("节点正文第一段")
                .doesNotContain("Meeting time", "Headquarters conference room");
        ExportFormattingContext formatting = wordExportService.lastRequest.formatting();
        assertThat(formatting.body().eastAsiaFontFamily()).isEqualTo("KaiTi");
        assertThat(formatting.body().latinFontFamily()).isEqualTo("Arial");
        assertThat(formatting.body().fontSizeHalfPoints()).isEqualTo(36);
        assertThat(formatting.body().indentationFirstLine()).isEqualTo(560);
        assertThat(formatting.body().lineSpacing().mode()).isEqualTo("AUTO");
        assertThat(formatting.body().lineSpacing().multipleHundred()).isEqualTo(150);
        assertThat(templateBodyFormatting.eastAsiaFontFamily()).isEqualTo("FangSong");
        assertThat(templateBodyFormatting.lineSpacing().multipleHundred()).isEqualTo(180);
        assertThat(wordExportService.lastRequest.traceSnapshot().structureMappingProfileId()).isEqualTo(mappingProfileId);
        assertThat(wordExportService.lastRequest.traceSnapshot().structureMappingVersion()).isEqualTo(1);
        assertThat(wordExportService.lastRequest.traceSnapshot().structureProfileSnapshot()).isNotNull();
        assertThat(wordExportService.lastRequest.traceSnapshot().mappingProfileSnapshot()).isSameAs(mapping);
        assertThat(wordExportService.lastRequest.traceSnapshot().formattingSnapshot()).isSameAs(formatting);
        assertThat(wordExportService.lastRequest.traceSnapshot().nodeSnapshot()).isInstanceOf(List.class);
    }

    @Test
    void blocksExportWhenPublishedMappingIsMissing() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(PLACEHOLDER_TITLE);
        Path templatePath = tempDir.resolve("missing-mapping-template.docx");
        Files.write(templatePath, templateBytes);
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(32L, 9L, "Missing mapping")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(emptyProfile()),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(new InMemoryExportRecordRepository()),
                null,
                new FixedDraftNodeRepository(List.of()),
                new FixedStructureMappingRepository(null),
                new FixedDocumentStructureProfileRepository(documentStructureProfile("title-1", "body-1"))
        );

        assertThatThrownBy(() -> service.exportDraft(32L))
                .isInstanceOf(WordExportException.class)
                .satisfies(error -> assertThat(((WordExportException) error).errorCode())
                .isEqualTo("STRUCTURE_MAPPING_REQUIRED"))
                .hasMessageContaining("导出前需要当前模板版本已有已发布的结构映射");
    }

    @Test
    void renderDraftForPreviewAllowsMissingPublishedMappingWithoutWritingExportRecord() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(
                PLACEHOLDER_TITLE,
                PLACEHOLDER_RECIPIENT,
                PLACEHOLDER_BODY,
                PLACEHOLDER_ATTACHMENT,
                PLACEHOLDER_SIGNATURE,
                PLACEHOLDER_DATE
        );
        Path templatePath = tempDir.resolve("preview-without-mapping-template.docx");
        Files.write(templatePath, templateBytes);
        InMemoryExportRecordRepository records = new InMemoryExportRecordRepository();
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(42L, 9L, "Preview without mapping")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(emptyProfile()),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(records),
                null,
                new FixedDraftNodeRepository(List.of()),
                new FixedStructureMappingRepository(null),
                new FixedDocumentStructureProfileRepository(documentStructureProfile("title-1", "body-1"))
        );

        DraftWordRenderResult result = service.renderDraftForPreview(42L);

        String text = DocxTestFactory.readText(result.content());
        assertThat(result.templateVersionId()).isEqualTo(9L);
        assertThat(text).contains("Year-end archive notice");
        assertThat(text).contains("Headquarters conference room");
        assertThat(records.savedRecord).isNull();
    }

    @Test
    void blocksExportWhenDocumentKindDisallowsAutoTemplate() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(PLACEHOLDER_TITLE);
        Path templatePath = tempDir.resolve("manual-template.docx");
        Files.write(templatePath, templateBytes);
        TemplateProfile profile = emptyProfile().withTemplateAnalysis(new TemplateAnalysisProfile(
                "MANUAL_OR_GUIDE",
                0.99d,
                "NOTICE",
                List.of(),
                List.of(),
                "manual",
                "TEST",
                "MANUAL_OR_GUIDE",
                List.of("FORMAT_GUIDE"),
                "BLOCK_AUTO_TEMPLATE",
                List.of("manual")
        ));
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(33L, 9L, "Manual kind")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(profile),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(new InMemoryExportRecordRepository()),
                null,
                new FixedDraftNodeRepository(List.of()),
                new FixedStructureMappingRepository(publishedMapping(9L, 56L,
                        mappingItem("title-1", "TITLE", "TITLE", 10),
                        mappingItem("body-1", "BODY", "BODY_PARAGRAPH", 20)
                )),
                new FixedDocumentStructureProfileRepository(documentStructureProfile("title-1", "body-1"))
        );

        assertThatThrownBy(() -> service.exportDraft(33L))
                .isInstanceOf(WordExportException.class)
                .satisfies(error -> assertThat(((WordExportException) error).errorCode())
                        .isEqualTo("DOCUMENT_KIND_EXPORT_BLOCKED"));
    }

    @Test
    void blocksExportWhenRequiredMappedSlotIsEmpty() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(PLACEHOLDER_TITLE, PLACEHOLDER_BODY);
        Path templatePath = tempDir.resolve("empty-node-template.docx");
        Files.write(templatePath, templateBytes);
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(34L, 9L, "Empty node")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(emptyProfile()),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(new InMemoryExportRecordRepository()),
                null,
                new FixedDraftNodeRepository(List.of(
                        draftNode(201L, 34L, 57L, "title-1", "TITLE", "TITLE", "节点标题", 10, DraftNodeFormatOverride.empty()),
                        draftNode(202L, 34L, 57L, "body-1", "BODY", "BODY_PARAGRAPH", "", 20, DraftNodeFormatOverride.empty())
                )),
                new FixedStructureMappingRepository(publishedMapping(9L, 57L,
                        mappingItem("title-1", "TITLE", "TITLE", 10),
                        mappingItem("body-1", "BODY", "BODY_PARAGRAPH", 20)
                )),
                new FixedDocumentStructureProfileRepository(documentStructureProfile("title-1", "body-1"))
        );

        assertThatThrownBy(() -> service.exportDraft(34L))
                .isInstanceOf(WordExportException.class)
                .satisfies(error -> {
                    WordExportException exception = (WordExportException) error;
                    assertThat(exception.errorCode()).isEqualTo("EXPORT_REQUIRED_SLOT_EMPTY");
                    assertThat(exception.getMessage()).contains("BODY");
                });
    }

    @Test
    void referenceDocumentExportUsesOriginalNodeReplacementStrategy() throws Exception {
        long draftId = 35L;
        long templateVersionId = 9L;
        long mappingProfileId = 58L;
        byte[] templateBytes = DocxTestFactory.speechReferenceDocument();
        Path templatePath = tempDir.resolve("reference-document-template.docx");
        Files.write(templatePath, templateBytes);
        TemplateProfile profile = emptyProfile().withTemplateAnalysis(new TemplateAnalysisProfile(
                "REFERENCE_DOCUMENT",
                0.95d,
                "UNKNOWN",
                List.of(),
                List.of(),
                "reference document",
                "TEST",
                "REFERENCE_DOCUMENT",
                List.of("COMPLETE_REFERENCE_DOCUMENT"),
                "REVIEW_AND_MAP",
                List.of()
        ));
        StructureMappingProfile mapping = publishedMapping(templateVersionId, mappingProfileId,
                mappingItem("paragraph-0", "TITLE", "TITLE", 10),
                mappingItem("paragraph-4", "BODY", "BODY_PARAGRAPH", 20)
        );
        InMemoryExportRecordRepository records = new InMemoryExportRecordRepository();
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(draftId, templateVersionId, "Legacy draft")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(profile),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(records),
                null,
                new FixedDraftNodeRepository(List.of(
                        draftNode(301L, draftId, mappingProfileId, "paragraph-0", "TITLE", "TITLE", "替换后的讲话标题", 10, DraftNodeFormatOverride.empty()),
                        draftNode(302L, draftId, mappingProfileId, "paragraph-4", "BODY", "BODY_PARAGRAPH", "替换后的第一段正文", 20, DraftNodeFormatOverride.empty())
                )),
                new FixedStructureMappingRepository(mapping),
                new FixedDocumentStructureProfileRepository(documentStructureProfile("paragraph-0", "paragraph-4"))
        );

        WordExportResult result = service.exportDraft(draftId);

        assertThat(records.savedRecord.traceSnapshot().strategy()).isEqualTo("ORIGINAL_NODE_REPLACEMENT");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result.content()))) {
            assertThat(document.getParagraphs().get(0).getText()).isEqualTo("替换后的讲话标题");
            assertThat(document.getParagraphs().get(4).getText()).isEqualTo("替换后的第一段正文");
            assertThat(document.getParagraphs()).hasSize(20);
        }
    }

    @Test
    void referenceDocumentExportRemovesDraftDeletedOriginalNodes() throws Exception {
        long draftId = 36L;
        long templateVersionId = 9L;
        long mappingProfileId = 59L;
        byte[] templateBytes = DocxTestFactory.speechReferenceDocument();
        Path templatePath = tempDir.resolve("reference-document-template-with-deleted-node.docx");
        Files.write(templatePath, templateBytes);
        TemplateProfile profile = emptyProfile().withTemplateAnalysis(new TemplateAnalysisProfile(
                "REFERENCE_DOCUMENT",
                0.95d,
                "UNKNOWN",
                List.of(),
                List.of(),
                "reference document",
                "TEST",
                "REFERENCE_DOCUMENT",
                List.of("COMPLETE_REFERENCE_DOCUMENT"),
                "REVIEW_AND_MAP",
                List.of()
        ));
        StructureMappingProfile mapping = publishedMapping(templateVersionId, mappingProfileId,
                mappingItem("paragraph-0", "TITLE", "TITLE", 10),
                mappingItem("paragraph-4", "BODY", "BODY_PARAGRAPH", 20),
                mappingItem("paragraph-5", "BODY", "BODY_PARAGRAPH", 30)
        );
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(draftId, templateVersionId, "Legacy draft")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(profile),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                new WordExportService(new InMemoryExportRecordRepository()),
                null,
                new FixedDraftNodeRepository(List.of(
                        draftNode(301L, draftId, mappingProfileId, "paragraph-0", "TITLE", "TITLE", "替换后的讲话标题", 10, DraftNodeFormatOverride.empty()),
                        draftNode(302L, draftId, mappingProfileId, "paragraph-4", "BODY", "BODY_PARAGRAPH", "删除后不应写入", 20, DraftNodeFormatOverride.empty(), "DELETED"),
                        draftNode(303L, draftId, mappingProfileId, "paragraph-5", "BODY", "BODY_PARAGRAPH", "保留的正文段落", 30, DraftNodeFormatOverride.empty())
                )),
                new FixedStructureMappingRepository(mapping),
                new FixedDocumentStructureProfileRepository(documentStructureProfile("paragraph-0", "paragraph-4", "paragraph-5"))
        );

        WordExportResult result = service.exportDraft(draftId);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result.content()))) {
            assertThat(document.getParagraphs()).hasSize(19);
            assertThat(document.getParagraphs())
                    .noneMatch(paragraph -> paragraph.getText().contains("删除后不应写入"));
        }
    }

    @Test
    void exportsDraftWithoutRequiringQualityCheckResult() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(PLACEHOLDER_TITLE, PLACEHOLDER_BODY);
        Path templatePath = tempDir.resolve("quality-independent-template.docx");
        Files.write(templatePath, templateBytes);
        CapturingWordExportService wordExportService = new CapturingWordExportService();

        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(sampleDraft(21L, 9L, "Unchecked export")),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new FixedTemplateProfileRepository(emptyProfile()),
                new FixedTemplateStructureFormattingRepository(Map.of()),
                new TemplateEffectiveFormattingService(),
                wordExportService
        );

        service.exportDraft(21L);

        assertThat(wordExportService.lastRequest).isNotNull();
        assertThat(wordExportService.lastRequest.values().get("正文")).contains("Meeting time");
    }

    @Test
    void wordExportRequestNormalizesNullFormattingToEmptyContext() {
        WordExportRequest request = new WordExportRequest("Test Template", 2, Map.of("BODY_PARAGRAPH", "body"));

        assertThat(request.formatting()).isSameAs(ExportFormattingContext.EMPTY);
    }

    private static DraftDetailDto sampleDraft(long draftId, Long templateVersionId, String title) {
        return new DraftDetailDto(
                draftId,
                "NOTICE",
                title,
                "DRAFT",
                templateVersionId,
                List.of(
                        new DraftBlockDto(1L, "TITLE", "Year-end archive notice", 10),
                        new DraftBlockDto(2L, "RECIPIENT", "All departments", 20),
                        new DraftBlockDto(3L, "BODY_PARAGRAPH", "1. Meeting time\n2026-05-07 09:30", 30),
                        new DraftBlockDto(4L, "BODY_PARAGRAPH", "2. Meeting location\nHeadquarters conference room", 31),
                        new DraftBlockDto(5L, "ATTACHMENT", "Agenda", 40),
                        new DraftBlockDto(6L, "SIGNATURE", "General Office", 50),
                        new DraftBlockDto(7L, "DATE", "2026-05-07", 60)
                )
        );
    }

    private static DraftDetailDto contaminatedDraft(long draftId, Long templateVersionId) {
        return new DraftDetailDto(
                draftId,
                "NOTICE",
                "Contaminated notice",
                "DRAFT",
                templateVersionId,
                List.of(
                        new DraftBlockDto(1L, "TITLE", "关于召开专题协调会的通知", 10),
                        new DraftBlockDto(2L, "RECIPIENT", "各部门、各直属单位", 20),
                        new DraftBlockDto(3L, "BODY_PARAGRAPH", """
                                各部门、各直属单位：
                                为统筹推进近期重点工作，及时协调解决跨部门事项，现将有关事项通知如下：
                                一、会议时间
                                2026年6月3日（星期三）上午9:30。
                                附件：会议议题征集表
                                示例单位办公室
                                2026年5月27日
                                """.trim(), 30),
                        new DraftBlockDto(4L, "SIGNATURE", "办公室", 50),
                        new DraftBlockDto(5L, "DATE", "2026年5月30日", 60)
                )
        );
    }

    private static TemplateProfile emptyProfile() {
        return new TemplateProfile(1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static TemplateStructureProfile structure(
            String key,
            String type,
            String alignment,
            Integer indentationFirstLine,
            Integer spacingBetween
    ) {
        return new TemplateStructureProfile(
                key,
                type,
                type,
                type + " preview",
                "PARAGRAPH",
                null,
                null,
                "PROFILE",
                new TemplateStructureFormattingProfile(
                        "FangSong",
                        32,
                        false,
                        alignment,
                        indentationFirstLine,
                        spacingBetween,
                        0,
                        0
                )
        );
    }

    private static StructureMappingProfile publishedMapping(
            long templateVersionId,
            long mappingProfileId,
            StructureMappingItem... items
    ) {
        return new StructureMappingProfile(
                mappingProfileId,
                templateVersionId,
                1,
                "PUBLISHED",
                List.of(items),
                List.of(),
                0,
                0,
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }

    private static StructureMappingItem mappingItem(String nodeKey, String role, String slotKey, int sortOrder) {
        return new StructureMappingItem(nodeKey, role, slotKey, "CONFIRMED", "USER", 1.0d, "", sortOrder);
    }

    private static DraftNode draftNode(
            long id,
            long draftId,
            long mappingProfileId,
            String nodeKey,
            String role,
            String slotKey,
            String content,
            int sortOrder,
            DraftNodeFormatOverride formatOverride
    ) {
        return draftNode(id, draftId, mappingProfileId, nodeKey, role, slotKey, content, sortOrder, formatOverride,
                content == null || content.isBlank() ? "EMPTY" : "USER_FILLED");
    }

    private static DraftNode draftNode(
            long id,
            long draftId,
            long mappingProfileId,
            String nodeKey,
            String role,
            String slotKey,
            String content,
            int sortOrder,
            DraftNodeFormatOverride formatOverride,
            String status
    ) {
        return new DraftNode(
                id,
                draftId,
                mappingProfileId,
                nodeKey,
                null,
                "PARAGRAPH",
                role,
                slotKey,
                role,
                content,
                sortOrder,
                status,
                formatOverride,
                Instant.now(),
                Instant.now()
        );
    }

    private static DocumentStructureProfile documentStructureProfile(String... nodeKeys) {
        List<DocumentNode> nodes = List.of(nodeKeys).stream()
                .map(nodeKey -> new DocumentNode(
                        nodeKey,
                        null,
                        "PARAGRAPH",
                        "UNKNOWN",
                        nodeKey,
                        nodeKey,
                        List.of(nodeKeys).indexOf(nodeKey),
                        "/" + nodeKey,
                        null,
                        List.of()
                ))
                .toList();
        return new DocumentStructureProfile(1, "hash", "document-structure-v1", nodes, List.of(), List.of(), List.of(), Instant.now());
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

    private record FixedTemplateVersionRepository(String filePath) implements TemplateVersionRepository {
        @Override
        public TemplateVersion create(long templateId, String originalFileName, String contentType, long fileSizeBytes, String filePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextVersionNo(long templateId) {
            return 3;
        }

        @Override
        public Optional<TemplateVersion> findById(long id) {
            return Optional.of(new TemplateVersion(
                    id,
                    3L,
                    2,
                    "notice-template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    100L,
                    filePath,
                    null,
                    "READY",
                    null,
                    null,
                    Instant.now()
            ));
        }

        @Override
        public void markParsed(long id, String profileHash) {
        }

        @Override
        public void markFailed(long id, String errorCode, String errorMessage) {
        }
    }

    private static final class FixedTemplateRepository implements TemplateRepository {
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
            return Optional.of(new TemplateSummary(id, "Test Template", "NOTICE", "ACTIVE"));
        }
    }

    private record FixedTemplateProfileRepository(TemplateProfile profile) implements TemplateProfileRepository {
        @Override
        public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.ofNullable(profile);
        }
    }

    private static final class CapturingTemplateProfileRepository implements TemplateProfileRepository {
        private final TemplateProfile profile;
        private Long lastRequestedTemplateVersionId;

        private CapturingTemplateProfileRepository(TemplateProfile profile) {
            this.profile = profile;
        }

        @Override
        public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
            this.lastRequestedTemplateVersionId = templateVersionId;
            return Optional.ofNullable(profile);
        }
    }

    private record FixedTemplateStructureFormattingRepository(
            Map<String, TemplateStructureFormattingProfile> overrides
    ) implements TemplateStructureFormattingRepository {
        @Override
        public Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId) {
            return overrides;
        }

        @Override
        public void saveOverride(
                long templateVersionId,
                String structureKey,
                TemplateStructureFormattingProfile formatting
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CapturingTemplateStructureFormattingRepository
            implements TemplateStructureFormattingRepository {
        private final Map<String, TemplateStructureFormattingProfile> overrides;
        private Long lastRequestedTemplateVersionId;

        private CapturingTemplateStructureFormattingRepository(
                Map<String, TemplateStructureFormattingProfile> overrides
        ) {
            this.overrides = overrides;
        }

        @Override
        public Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId) {
            this.lastRequestedTemplateVersionId = templateVersionId;
            return overrides;
        }

        @Override
        public void saveOverride(
                long templateVersionId,
                String structureKey,
                TemplateStructureFormattingProfile formatting
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryExportRecordRepository implements ExportRecordRepository {
        private String savedStatus;
        private ExportRecord savedRecord;

        @Override
        public void save(ExportRecord record) {
            this.savedRecord = record;
            this.savedStatus = record.status();
        }
    }

    private record FixedDraftNodeRepository(List<DraftNode> nodes) implements DraftNodeRepository {
        @Override
        public List<DraftNode> findByDraftId(long draftId) {
            return nodes.stream()
                    .filter(node -> node.draftId() == draftId)
                    .toList();
        }

        @Override
        public boolean existsByDraftId(long draftId) {
            return !findByDraftId(draftId).isEmpty();
        }

        @Override
        public List<DraftNode> replaceForDraft(long draftId, List<DraftNode> nodes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DraftNode> updateContent(long draftId, long nodeId, String content, String status) {
            throw new UnsupportedOperationException();
        }
    }

    private record FixedStructureMappingRepository(StructureMappingProfile mapping) implements StructureMappingRepository {
        @Override
        public StructureMappingProfile save(StructureMappingProfile profile, com.gongwen.assistant.security.CurrentUser currentUser) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<StructureMappingProfile> findLatest(long templateVersionId) {
            return Optional.ofNullable(mapping);
        }

        @Override
        public Optional<StructureMappingProfile> findLatestByStatus(long templateVersionId, String status) {
            return Optional.ofNullable(mapping)
                    .filter(profile -> profile.templateVersionId() == templateVersionId)
                    .filter(profile -> status.equals(profile.status()));
        }

        @Override
        public int nextVersionNo(long templateVersionId) {
            throw new UnsupportedOperationException();
        }
    }

    private record FixedDocumentStructureProfileRepository(
            DocumentStructureProfile profile
    ) implements DocumentStructureProfileRepository {
        @Override
        public void save(long templateVersionId, DocumentStructureProfile profile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DocumentStructureProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.ofNullable(profile);
        }
    }

    private static final class CapturingWordExportService extends WordExportService {
        private WordExportRequest lastRequest;

        private CapturingWordExportService() {
            super(new InMemoryExportRecordRepository());
        }

        @Override
        public WordExportResult export(byte[] templateBytes, WordExportRequest request) {
            this.lastRequest = request;
            return new WordExportResult("captured.docx", templateBytes);
        }
    }
}
