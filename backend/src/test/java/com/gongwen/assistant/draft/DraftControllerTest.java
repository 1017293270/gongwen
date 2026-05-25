package com.gongwen.assistant.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DraftController.class)
class DraftControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DraftService draftService;

    @Test
    void createsDraft() throws Exception {
        when(draftService.createDraft(any())).thenReturn(sampleDraft("测试通知"));

        mockMvc.perform(post("/api/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateDraftRequest("NOTICE", "测试通知"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("测试通知"));
    }

    @Test
    void getsDraft() throws Exception {
        when(draftService.getDraft(1L)).thenReturn(sampleDraft("测试通知"));

        mockMvc.perform(get("/api/drafts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocks[0].blockType").value("TITLE"));
    }

    @Test
    void updatesBlocks() throws Exception {
        when(draftService.updateBlocks(eq(1L), any())).thenReturn(sampleDraft("新标题"));

        UpdateDraftBlocksRequest request = new UpdateDraftBlocksRequest(List.of(
                new DraftBlockUpdateRequest("TITLE", "新标题", 10)
        ));

        mockMvc.perform(put("/api/drafts/1/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新标题"));
    }

    @Test
    void returnsNotFoundForMissingDraft() throws Exception {
        when(draftService.getDraft(99L)).thenThrow(new DraftNotFoundException(99L));

        mockMvc.perform(get("/api/drafts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }

    private DraftDetailDto sampleDraft(String title) {
        return new DraftDetailDto(1L, "NOTICE", title, "DRAFT", List.of(
                new DraftBlockDto(1L, "TITLE", title, 10),
                new DraftBlockDto(2L, "BODY_PARAGRAPH", "正文", 30)
        ));
    }
}
