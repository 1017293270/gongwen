package com.gongwen.assistant.exporting;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.exporting.word.ExportFormattingContext;
import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.template.TemplateRepository;
import com.gongwen.assistant.template.TemplateSummary;
import com.gongwen.assistant.template.TemplateVersion;
import com.gongwen.assistant.template.TemplateVersionRepository;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileParser;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import com.gongwen.assistant.template.profile.TemplateStructureProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

        @Override
        public void save(ExportRecord record) {
            this.savedStatus = record.status();
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
