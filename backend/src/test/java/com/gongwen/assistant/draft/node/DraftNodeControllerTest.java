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
import org.springframework.security.test.context.support.WithMockUser;

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
@WithMockUser(roles = {"DRAFTER"})
class DraftNodeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DraftNodeService service;

    @Test
    void initializesNodes() throws Exception {
        when(service.initializeNodes(eq(5L), any(InitializeDraftNodesRequest.class)))
                .thenReturn(List.of(sampleNode("TITLE", "测试通知")));

        mockMvc.perform(post("/api/drafts/5/nodes/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InitializeDraftNodesRequest("TEMPLATE_HEADINGS_ONLY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].role").value("TITLE"))
                .andExpect(jsonPath("$.data[0].content").value("测试通知"));

        verify(service).initializeNodes(eq(5L), any(InitializeDraftNodesRequest.class));
    }

    @Test
    void appliesOutline() throws Exception {
        when(service.applyOutline(eq(5L), any(ApplyOutlineRequest.class)))
                .thenReturn(new ApplyOutlineResponse(
                        List.of(sampleNode("BODY", "")),
                        List.of(new AppliedOutlineSectionTarget(0, "一、总体要求", 1, 21L, 22L)),
                        List.of("模板未识别出二级标题格式")
                ));

        mockMvc.perform(post("/api/drafts/5/nodes/apply-outline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "titleSuggestion": "测试通知",
                                  "sections": [
                                    {"level": 1, "heading": "一、总体要求", "points": ["说明背景"], "sourceRefs": ["meeting.docx"]}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sectionTargets[0].bodyNodeId").value(22))
                .andExpect(jsonPath("$.data.formattingWarnings[0]").value("模板未识别出二级标题格式"));
    }

    @Test
    void reinitializesNodes() throws Exception {
        when(service.reinitializeNodes(eq(5L), any(ReinitializeDraftNodesRequest.class)))
                .thenReturn(List.of(sampleNode("BODY", "重建后的正文")));

        mockMvc.perform(post("/api/drafts/5/nodes/reinitialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ReinitializeDraftNodesRequest(
                                "FROM_SOURCE_DOCUMENT",
                                true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].role").value("BODY"))
                .andExpect(jsonPath("$.data[0].content").value("重建后的正文"));

        verify(service).reinitializeNodes(eq(5L), any(ReinitializeDraftNodesRequest.class));
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
        when(service.initializeNodes(eq(5L), any(InitializeDraftNodesRequest.class))).thenThrow(new DraftNodeException(
                "STRUCTURE_MAPPING_REQUIRED",
                "Published structure mapping is required before nodes can be initialized"
        ));

        mockMvc.perform(post("/api/drafts/5/nodes/initialize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
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
                DraftNodeMetadata.empty(),
                Instant.parse("2026-05-30T00:00:00Z"),
                Instant.parse("2026-05-30T00:00:00Z")
        );
    }
}
