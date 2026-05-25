package com.gongwen.assistant.exporting;

import com.gongwen.assistant.support.DocxTestFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WordExportServiceTest {
    private final InMemoryExportRecordRepository repository = new InMemoryExportRecordRepository();
    private final WordExportService service = new WordExportService(repository);

    @Test
    void exportsDocxAndRecordsSuccess() {
        byte[] template = DocxTestFactory.docxWithParagraphs("{{标题}}", "{{正文}}");

        WordExportResult result = service.export(template, new WordExportRequest(
                "通知模板",
                1,
                Map.of("标题", "测试标题", "正文", "测试正文")
        ));

        assertThat(result.fileName()).isEqualTo("通知模板-v1.docx");
        assertThat(DocxTestFactory.readText(result.content())).contains("测试标题", "测试正文");
        assertThat(repository.savedStatus).isEqualTo("SUCCESS");
    }

    @Test
    void recordsFailureWhenValuesAreMissing() {
        byte[] template = DocxTestFactory.docxWithParagraphs("{{标题}}", "{{正文}}");

        assertThatThrownBy(() -> service.export(template, new WordExportRequest(
                "通知模板",
                1,
                Map.of("标题", "测试标题")
        ))).isInstanceOf(WordExportException.class)
                .hasMessageContaining("正文");

        assertThat(repository.savedStatus).isEqualTo("FAILED");
        assertThat(repository.savedErrorCode).isEqualTo("MISSING_TEMPLATE_VALUE");
    }

    private static final class InMemoryExportRecordRepository implements ExportRecordRepository {
        private String savedStatus;
        private String savedErrorCode;

        @Override
        public void save(ExportRecord record) {
            this.savedStatus = record.status();
            this.savedErrorCode = record.errorCode();
        }
    }
}
