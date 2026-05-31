package com.gongwen.assistant.template;

import com.gongwen.assistant.documentstructure.DocumentStructureExtractor;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.template.profile.TemplatePlaceholderProfile;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileParser;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateUploadServiceTest {
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Test
    void localStorageSavesTemplateUnderConfiguredDirectory() throws IOException {
        Path storageDir = Files.createTempDirectory("template-storage-test");
        LocalTemplateStorage storage = new LocalTemplateStorage(new TemplateProperties(storageDir.toString(), 20));

        String path = storage.save("notice-template.docx", "docx", "template".getBytes());

        assertThat(Path.of(path)).startsWith(storageDir);
        assertThat(Files.readString(Path.of(path))).isEqualTo("template");
        assertThat(path).endsWith(".docx");
    }

    @Test
    void localStorageSanitizesPathTraversalFileName() throws IOException {
        Path storageDir = Files.createTempDirectory("template-storage-test");
        LocalTemplateStorage storage = new LocalTemplateStorage(new TemplateProperties(storageDir.toString(), 20));

        String path = storage.save("..\\..\\evil.docx", "docx", "template".getBytes());

        assertThat(Path.of(path)).startsWith(storageDir);
        assertThat(Files.exists(Path.of(path))).isTrue();
        assertThat(Path.of(path).getFileName().toString()).doesNotContain("..", "\\", "/");
    }

    @Test
    void templateVersionCreateIsTransactional() throws NoSuchMethodException {
        Method create = JdbcTemplateVersionRepository.class.getMethod(
                "create",
                long.class,
                String.class,
                String.class,
                long.class,
                String.class);

        assertThat(create.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void templateVersionCreateLocksParentTemplateBeforeVersionCalculation() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("for update"), eq(7L)))
                .thenThrow(new IllegalStateException("lock failed"));
        JdbcTemplateVersionRepository repository = new JdbcTemplateVersionRepository(jdbcTemplate);

        assertThatThrownBy(() -> repository.create(7L, "notice.docx", "application/docx", 8, "templates/notice.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("lock failed");

        verify(jdbcTemplate).queryForList("select id from document_template where id = ? for update", 7L);
        verify(jdbcTemplate, never()).queryForObject(
                contains("document_template_version"),
                eq(Integer.class),
                anyLong());
    }

    @Test
    void markFailedClearsParsedMetadata() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplateVersionRepository repository = new JdbcTemplateVersionRepository(jdbcTemplate);

        repository.markFailed(9L, "TEMPLATE_PARSE_FAILED", "bad template");

        verify(jdbcTemplate).update(
                contains("profile_hash = null"),
                eq("TEMPLATE_PARSE_FAILED"),
                eq("bad template"),
                eq(9L));
    }

    @Test
    void profileRepositoryStoresProfileForTemplateVersion() {
        InMemoryTemplateProfileRepository repository = new InMemoryTemplateProfileRepository();
        TemplateProfile profile = new TemplateProfile(
                1,
                List.of(),
                List.of(new TemplatePlaceholderProfile("title", "PARAGRAPH", "paragraph-0", null, null, false)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        repository.save(9L, profile, "hash-1");

        assertThat(repository.findByTemplateVersionId(9L)).contains(profile);
    }

    @Test
    void uploadStoresTemplateVersionAndProfile() {
        byte[] content = DocxTestFactory.docxWithOfficialStyles();
        InMemoryTemplateVersionRepository versionRepository = new InMemoryTemplateVersionRepository();
        InMemoryTemplateProfileRepository profileRepository = new InMemoryTemplateProfileRepository();
        TemplateUploadService service = new TemplateUploadService(
                (originalFileName, fileExtension, bytes) -> "storage/templates/notice.docx",
                versionRepository,
                profileRepository,
                new TemplateProfileParser(),
                new TemplateProperties("storage/templates", 20)
        );

        TemplateUploadResponse response = service.upload(3L, "notice.docx", DOCX_CONTENT_TYPE, content);

        assertThat(response.templateVersionId()).isEqualTo(1L);
        assertThat(response.versionNo()).isEqualTo(1);
        assertThat(response.parseStatus()).isEqualTo("READY");
        assertThat(response.placeholderCount()).isEqualTo(2);
        assertThat(response.styleCount()).isEqualTo(2);
        assertThat(response.validationCount()).isZero();
        assertThat(response.validationCodes()).isEmpty();
        assertThat(profileRepository.findByTemplateVersionId(1L)).isPresent();
        assertThat(profileRepository.profileHashes.get(1L)).hasSize(64);
        assertThat(versionRepository.findById(1L))
                .get()
                .extracting(TemplateVersion::parseStatus, TemplateVersion::profileHash)
                .containsExactly("READY", profileRepository.profileHashes.get(1L));
    }

    @Test
    void uploadAddsTemplateAnalysisWhenNoPlaceholderExists() {
        byte[] content = DocxTestFactory.docxWithParagraphs("关于开展年度档案整理工作的通知", "各部门应按时完成归档工作。");
        InMemoryTemplateVersionRepository versionRepository = new InMemoryTemplateVersionRepository();
        InMemoryTemplateProfileRepository profileRepository = new InMemoryTemplateProfileRepository();
        TemplateUploadService service = new TemplateUploadService(
                (originalFileName, fileExtension, bytes) -> "storage/templates/notice.docx",
                versionRepository,
                profileRepository,
                new TemplateProfileParser(),
                new TemplateProperties("storage/templates", 20)
        );

        service.upload(3L, "notice.docx", DOCX_CONTENT_TYPE, content);

        TemplateProfile profile = profileRepository.findByTemplateVersionId(1L).orElseThrow();
        assertThat(profile.placeholders()).isEmpty();
        assertThat(profile.templateAnalysis()).isNotNull();
        assertThat(profile.templateAnalysis().documentKind()).isEqualTo("STYLE_TEMPLATE");
        assertThat(profile.templateAnalysis().recommendedWorkflow()).isEqualTo("REVIEW_AND_ADD_PLACEHOLDERS");
        assertThat(profile.templateAnalysis().suggestedPlaceholders())
                .extracting("field")
                .contains("标题", "正文");
    }

    @Test
    void uploadPersistsDocumentStructureProfileWhenRepositoryIsConfigured() {
        byte[] content = DocxTestFactory.docxWithOfficialStyles();
        InMemoryTemplateVersionRepository versionRepository = new InMemoryTemplateVersionRepository();
        InMemoryTemplateProfileRepository profileRepository = new InMemoryTemplateProfileRepository();
        InMemoryDocumentStructureProfileRepository structureRepository = new InMemoryDocumentStructureProfileRepository();
        TemplateUploadService service = new TemplateUploadService(
                (originalFileName, fileExtension, bytes) -> "storage/templates/notice.docx",
                versionRepository,
                profileRepository,
                new TemplateProfileParser(),
                new TemplateProperties("storage/templates", 20),
                new TemplateIntelligenceService(new com.gongwen.assistant.ai.MockModelAdapter()),
                new InMemoryTemplateRepository(),
                new DocumentStructureExtractor(),
                structureRepository
        );

        service.upload(3L, "notice.docx", DOCX_CONTENT_TYPE, content);

        DocumentStructureProfile structureProfile = structureRepository.findByTemplateVersionId(1L).orElseThrow();
        assertThat(structureProfile.sourceFileHash()).hasSize(64);
        assertThat(structureProfile.extractorVersion()).isEqualTo("document-structure-v2");
        assertThat(structureProfile.nodes())
                .extracting("nodeType")
                .contains("PARAGRAPH", "HEADER_PARAGRAPH");
        assertThat(structureProfile.nodes())
                .extracting("text")
                .contains("{{标题}}", "{{正文}}", "机关公文");
        assertThat(structureProfile.nodes())
                .extracting("roleSuggestion")
                .contains("TITLE", "BODY", "STATIC_TEXT");
    }

    @Test
    void uploadMarksPlaceholderTemplatesWithStableDocumentKind() {
        byte[] content = DocxTestFactory.docxWithOfficialStyles();
        InMemoryTemplateVersionRepository versionRepository = new InMemoryTemplateVersionRepository();
        InMemoryTemplateProfileRepository profileRepository = new InMemoryTemplateProfileRepository();
        TemplateUploadService service = new TemplateUploadService(
                (originalFileName, fileExtension, bytes) -> "storage/templates/notice.docx",
                versionRepository,
                profileRepository,
                new TemplateProfileParser(),
                new TemplateProperties("storage/templates", 20)
        );

        service.upload(3L, "notice-template.docx", DOCX_CONTENT_TYPE, content);

        TemplateProfile profile = profileRepository.findByTemplateVersionId(1L).orElseThrow();
        assertThat(profile.templateAnalysis()).isNotNull();
        assertThat(profile.templateAnalysis().templateKind()).isEqualTo("STANDARD_PLACEHOLDER_TEMPLATE");
        assertThat(profile.templateAnalysis().documentKind()).isEqualTo("PLACEHOLDER_TEMPLATE");
        assertThat(profile.templateAnalysis().reasonCodes()).contains("EXPLICIT_PLACEHOLDERS");
        assertThat(profile.templateAnalysis().blockingWarnings()).isEmpty();
    }

    @Test
    void uploadBlocksManualGuideDocumentsFromAutoTemplateFlow() {
        byte[] content = DocxTestFactory.docxWithManualGuideLikeDocument();
        InMemoryTemplateVersionRepository versionRepository = new InMemoryTemplateVersionRepository();
        InMemoryTemplateProfileRepository profileRepository = new InMemoryTemplateProfileRepository();
        TemplateUploadService service = new TemplateUploadService(
                (originalFileName, fileExtension, bytes) -> "storage/templates/manual.docx",
                versionRepository,
                profileRepository,
                new TemplateProfileParser(),
                new TemplateProperties("storage/templates", 20)
        );

        service.upload(3L, "公文使用手册.docx", DOCX_CONTENT_TYPE, content);

        TemplateProfile profile = profileRepository.findByTemplateVersionId(1L).orElseThrow();
        assertThat(profile.templateAnalysis()).isNotNull();
        assertThat(profile.templateAnalysis().documentKind()).isEqualTo("MANUAL_OR_GUIDE");
        assertThat(profile.templateAnalysis().recommendedWorkflow()).isEqualTo("BLOCK_AUTO_TEMPLATE");
        assertThat(profile.templateAnalysis().reasonCodes())
                .contains("MANUAL_OR_GUIDE_KEYWORD", "FORMAT_INSTRUCTION_TEXT");
        assertThat(profile.templateAnalysis().blockingWarnings())
                .contains("该文件更像公文使用手册或格式说明，不应直接发布为自动套版模板。");
        assertThat(profile.templateAnalysis().suggestedPlaceholders()).isEmpty();
    }

    @Test
    void uploadRejectsNonDocxTemplate() {
        InMemoryTemplateVersionRepository versionRepository = new InMemoryTemplateVersionRepository();
        TemplateUploadService service = new TemplateUploadService(
                (originalFileName, fileExtension, bytes) -> "storage/templates/bad.pdf",
                versionRepository,
                new InMemoryTemplateProfileRepository(),
                new TemplateProfileParser(),
                new TemplateProperties("storage/templates", 20)
        );

        assertThatThrownBy(() -> service.upload(3L, "bad.pdf", "application/pdf", "pdf".getBytes()))
                .isInstanceOfSatisfying(TemplateException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo("TEMPLATE_TYPE_NOT_ALLOWED"));

        assertThat(versionRepository.versions).isEmpty();
    }

    private static class InMemoryTemplateVersionRepository implements TemplateVersionRepository {
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

    private static class InMemoryTemplateProfileRepository implements TemplateProfileRepository {
        private final Map<Long, TemplateProfile> profiles = new HashMap<>();
        private final Map<Long, String> profileHashes = new HashMap<>();

        @Override
        public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
            profiles.put(templateVersionId, profile);
            profileHashes.put(templateVersionId, profileHash);
        }

        @Override
        public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.ofNullable(profiles.get(templateVersionId));
        }
    }

    private static class InMemoryDocumentStructureProfileRepository implements DocumentStructureProfileRepository {
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

    private static class InMemoryTemplateRepository implements TemplateRepository {
        @Override
        public TemplateSummary create(String templateName, String documentTypeCode) {
            return new TemplateSummary(1L, templateName, documentTypeCode, "ACTIVE");
        }

        @Override
        public List<TemplateSummary> findAll(String documentTypeCode) {
            return List.of();
        }

        @Override
        public Optional<TemplateSummary> findById(long id) {
            return Optional.of(new TemplateSummary(id, "测试模板", "NOTICE", "ACTIVE"));
        }
    }
}
