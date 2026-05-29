package com.gongwen.assistant.exporting;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.support.DocxTestFactory;
import com.gongwen.assistant.template.TemplateRepository;
import com.gongwen.assistant.template.TemplateSummary;
import com.gongwen.assistant.template.TemplateVersion;
import com.gongwen.assistant.template.TemplateVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftWordExportServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void exportsBoundDraftWithSelectedTemplateVersion() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(
                "{{标题}}",
                "{{主送}}：",
                "{{正文}}",
                "{{附件}}",
                "{{落款}}",
                "{{日期}}"
        );
        Path templatePath = tempDir.resolve("notice-template.docx");
        Files.write(templatePath, templateBytes);

        InMemoryExportRecordRepository records = new InMemoryExportRecordRepository();
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(new DraftDetailDto(
                        1L,
                        "NOTICE",
                        "测试通知",
                        "DRAFT",
                        9L,
                        List.of(
                                new DraftBlockDto(1L, "TITLE", "关于开展年度档案整理工作的通知", 10),
                                new DraftBlockDto(2L, "RECIPIENT", "各部门、各直属单位", 20),
                                new DraftBlockDto(3L, "BODY_PARAGRAPH", "一、会议时间\n2026年6月3日上午9:30。", 30),
                                new DraftBlockDto(4L, "BODY_PARAGRAPH", "二、会议地点\n公司总部三楼第一会议室。", 31),
                                new DraftBlockDto(5L, "ATTACHMENT", "无", 40),
                                new DraftBlockDto(6L, "SIGNATURE", "办公室", 50),
                                new DraftBlockDto(7L, "DATE", "2026年5月27日", 60)
                        )
                )),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new WordExportService(records)
        );

        WordExportResult result = service.exportDraft(1L);

        String text = DocxTestFactory.readText(result.content());
        assertThat(result.fileName()).isEqualTo("测试模板-v2.docx");
        assertThat(text).contains("关于开展年度档案整理工作的通知");
        assertThat(text).contains("各部门、各直属单位：");
        assertThat(text).contains("一、会议时间");
        assertThat(text).contains("公司总部三楼第一会议室。");
        assertThat(text).contains("办公室");
        assertThat(records.savedStatus).isEqualTo("SUCCESS");
    }

    @Test
    void exportsCurrentDraftContentWhenTemplateHasNoPlaceholders() throws Exception {
        byte[] templateBytes = DocxTestFactory.docxWithParagraphs(
                "示例单位文件",
                "这是一段模板里的示例正文，不应该原样出现在导出结果中。"
        );
        Path templatePath = tempDir.resolve("reference-template.docx");
        Files.write(templatePath, templateBytes);

        InMemoryExportRecordRepository records = new InMemoryExportRecordRepository();
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(new DraftDetailDto(
                        1L,
                        "NOTICE",
                        "测试通知",
                        "DRAFT",
                        9L,
                        List.of(
                                new DraftBlockDto(1L, "TITLE", "关于开展年度档案整理工作的通知111", 10),
                                new DraftBlockDto(2L, "RECIPIENT", "各部门、各直属单位", 20),
                                new DraftBlockDto(3L, "BODY_PARAGRAPH", "一、会议时间\n2026年6月3日上午9:30。", 30),
                                new DraftBlockDto(4L, "BODY_PARAGRAPH", "二、会议地点\n公司总部三楼第一会议室。", 31),
                                new DraftBlockDto(5L, "ATTACHMENT", "无", 40),
                                new DraftBlockDto(6L, "SIGNATURE", "办公室", 50),
                                new DraftBlockDto(7L, "DATE", "2026年5月27日", 60)
                        )
                )),
                new FixedTemplateVersionRepository(templatePath.toString()),
                new FixedTemplateRepository(),
                new WordExportService(records)
        );

        WordExportResult result = service.exportDraft(1L);

        String text = DocxTestFactory.readText(result.content());
        assertThat(text).contains("关于开展年度档案整理工作的通知111");
        assertThat(text).contains("各部门、各直属单位");
        assertThat(text).contains("一、会议时间");
        assertThat(text).contains("2026年6月3日上午9:30。");
        assertThat(text).contains("二、会议地点");
        assertThat(text).contains("公司总部三楼第一会议室。");
        assertThat(text).contains("办公室");
        assertThat(text).doesNotContain("这是一段模板里的示例正文");
        assertThat(records.savedStatus).isEqualTo("SUCCESS");
    }

    @Test
    void failsWhenDraftHasNoTemplateVersion() {
        DraftWordExportService service = new DraftWordExportService(
                new FixedDraftRepository(new DraftDetailDto(
                        1L,
                        "NOTICE",
                        "测试通知",
                        "DRAFT",
                        null,
                        List.of(new DraftBlockDto(1L, "TITLE", "测试通知", 10))
                )),
                new FixedTemplateVersionRepository("missing.docx"),
                new FixedTemplateRepository(),
                new WordExportService(new InMemoryExportRecordRepository())
        );

        assertThatThrownBy(() -> service.exportDraft(1L))
                .isInstanceOf(WordExportException.class)
                .hasMessageContaining("请先选择套版模板");
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
            return Optional.of(new TemplateSummary(id, "测试模板", "NOTICE", "ACTIVE"));
        }
    }

    private static final class InMemoryExportRecordRepository implements ExportRecordRepository {
        private String savedStatus;

        @Override
        public void save(ExportRecord record) {
            this.savedStatus = record.status();
        }
    }
}
