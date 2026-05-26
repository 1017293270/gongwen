package com.gongwen.assistant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongwen.assistant.draft.DraftNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiOutlineController.class)
class AiOutlineControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiOutlineService aiOutlineService;

    @Test
    void generatesOutline() throws Exception {
        UUID traceId = UUID.randomUUID();
        when(aiOutlineService.generateOutline(eq(1L), any())).thenReturn(new AiOutlineResponse(
                traceId,
                "测试通知",
                List.of(new AiOutlineSection("一、主要事项", List.of("说明安排"))),
                List.of("会议时间")
        ));

        mockMvc.perform(post("/api/drafts/1/ai/outline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AiOutlineRequest("突出要求"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.traceId").value(traceId.toString()))
                .andExpect(jsonPath("$.data.titleSuggestion").value("测试通知"))
                .andExpect(jsonPath("$.data.sections[0].heading").value("一、主要事项"));
    }

    @Test
    void returnsNotFoundForMissingDraft() throws Exception {
        when(aiOutlineService.generateOutline(eq(99L), any())).thenThrow(new DraftNotFoundException(99L));

        mockMvc.perform(post("/api/drafts/99/ai/outline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AiOutlineRequest(""))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }

    @Test
    void returnsBadRequestForLongInstruction() throws Exception {
        when(aiOutlineService.generateOutline(eq(1L), any()))
                .thenThrow(new AiOutlineException("AI_OUTLINE_INSTRUCTION_TOO_LONG", "补充要求不能超过 1000 字"));

        mockMvc.perform(post("/api/drafts/1/ai/outline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AiOutlineRequest("长".repeat(1001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AI_OUTLINE_INSTRUCTION_TOO_LONG"));
    }
}
