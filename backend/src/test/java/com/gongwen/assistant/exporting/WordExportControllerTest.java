package com.gongwen.assistant.exporting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WordExportController.class)
@AutoConfigureMockMvc(addFilters = false)
class WordExportControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WordExportService exportService;

    @MockBean
    private DraftWordExportService draftWordExportService;

    @MockBean
    private BuiltinDocxTemplateFactory templateFactory;

    @MockBean
    private ExportRecordService exportRecordService;

    @Test
    void listsExportRecords() throws Exception {
        when(exportRecordService.listRecords()).thenReturn(List.of(new ExportRecordSummary(
                7L,
                3L,
                "会议通知草稿",
                "NOTICE",
                2L,
                9L,
                "通知模板",
                4,
                "通知模板-v4.docx",
                "SUCCESS",
                null,
                null,
                true,
                Instant.parse("2026-05-30T09:30:00Z")
        )));

        mockMvc.perform(get("/api/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].draftTitle").value("会议通知草稿"))
                .andExpect(jsonPath("$.data[0].templateVersionId").value(9))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].canDownload").value(true));
    }

    @Test
    void downloadsHistoricalExportFile() throws Exception {
        when(exportRecordService.download(7L)).thenReturn(new ExportRecordFile(
                "通知模板-v4.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx-bytes".getBytes()
        ));

        mockMvc.perform(get("/api/exports/7/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Type", MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document").toString()));
    }

    @Test
    void returnsNotFoundForMissingExportRecordDownload() throws Exception {
        when(exportRecordService.download(404L))
                .thenThrow(new ExportRecordException("EXPORT_RECORD_NOT_FOUND", "导出记录不存在或无权访问"));

        mockMvc.perform(get("/api/exports/404/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("EXPORT_RECORD_NOT_FOUND"));
    }
}
