package com.gongwen.assistant.draft.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gongwen.assistant.draft.DraftNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DraftNodeController.class)
@AutoConfigureMockMvc(addFilters = false)
class DraftNodeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DraftNodeService service;

    @Test
    void initializesNodes() throws Exception {
        when(service.initializeNodes(5L)).thenReturn(List.of(sampleNode("TITLE", "测试通知")));

        mockMvc.perform(post("/api/drafts/5/nodes/initialize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].role").value("TITLE"))
                .andExpect(jsonPath("$.data[0].content").value("测试通知"));
    }

    @Test
    void listsNodes() throws Exception {
        when(service.listNodes(5L)).thenReturn(List.of(sampleNode("BODY", "正文内容")));

        mockMvc.perform(get("/api/drafts/5/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].templateNodeKey").value("body-node"))
                .andExpect(jsonPath("$.data[0].status").value("USER_FILLED"));
    }

    @Test
    void updatesNodeContent() throws Exception {
        when(service.updateNode(eq(5L), eq(12L), any(UpdateDraftNodeRequest.class)))
                .thenReturn(sampleNode("BODY", "修改后的正文"));

        mockMvc.perform(put("/api/drafts/5/nodes/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateDraftNodeRequest(
                                "修改后的正文",
                                "USER_MODIFIED_AFTER_AI"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("修改后的正文"));

        verify(service).updateNode(eq(5L), eq(12L), any(UpdateDraftNodeRequest.class));
    }

    @Test
    void updatesNodeFormatOverride() throws Exception {
        when(service.saveFormatOverride(eq(5L), eq(12L), any(DraftNodeFormatOverride.class)))
                .thenReturn(sampleNode("BODY", "正文", new DraftNodeFormatOverride(
                        "KaiTi",
                        "Times New Roman",
                        16.0,
                        true,
                        "CENTER",
                        560,
                        "EXACT",
                        590,
                        120,
                        240
                ), "FORMAT_OVERRIDDEN"));

        mockMvc.perform(put("/api/drafts/5/nodes/12/format-override")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DraftNodeFormatOverride(
                                "KaiTi",
                                "Times New Roman",
                                16.0,
                                true,
                                "CENTER",
                                560,
                                "EXACT",
                                590,
                                120,
                                240
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FORMAT_OVERRIDDEN"))
                .andExpect(jsonPath("$.data.formatOverride.eastAsiaFont").value("KaiTi"))
                .andExpect(jsonPath("$.data.formatOverride.lineSpacingTwip").value(590));

        verify(service).saveFormatOverride(eq(5L), eq(12L), any(DraftNodeFormatOverride.class));
    }

    @Test
    void restoresNodeTemplateDefaultFormatting() throws Exception {
        when(service.restoreTemplateDefaultFormatting(5L, 12L))
                .thenReturn(sampleNode("BODY", "正文"));

        mockMvc.perform(delete("/api/drafts/5/nodes/12/format-override"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formatOverride.eastAsiaFont").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("USER_FILLED"));

        verify(service).restoreTemplateDefaultFormatting(5L, 12L);
    }

    @Test
    void returnsUnprocessableWhenMappingIsMissing() throws Exception {
        when(service.initializeNodes(5L)).thenThrow(new DraftNodeException(
                "STRUCTURE_MAPPING_REQUIRED",
                "Published structure mapping is required before nodes can be initialized"
        ));

        mockMvc.perform(post("/api/drafts/5/nodes/initialize"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("STRUCTURE_MAPPING_REQUIRED"));
    }

    @Test
    void returnsNotFoundWhenDraftIsNotAccessible() throws Exception {
        when(service.listNodes(5L)).thenThrow(new DraftNotFoundException(5L));

        mockMvc.perform(get("/api/drafts/5/nodes"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }

    private DraftNodeDto sampleNode(String role, String content) {
        return sampleNode(role, content, DraftNodeFormatOverride.empty(), "USER_FILLED");
    }

    private DraftNodeDto sampleNode(String role, String content, DraftNodeFormatOverride formatOverride, String status) {
        return new DraftNodeDto(
                12L,
                5L,
                21L,
                "BODY".equals(role) ? "body-node" : "title-node",
                null,
                "PARAGRAPH",
                role,
                "BODY".equals(role) ? "body" : "title",
                "BODY".equals(role) ? "正文" : "标题",
                content,
                10,
                status,
                formatOverride,
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );
    }
}
