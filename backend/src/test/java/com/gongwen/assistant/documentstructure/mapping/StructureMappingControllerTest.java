package com.gongwen.assistant.documentstructure.mapping;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StructureMappingController.class)
@AutoConfigureMockMvc(addFilters = false)
class StructureMappingControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StructureMappingService service;

    @Test
    void returnsStructureMapping() throws Exception {
        when(service.getMapping(9L)).thenReturn(profile("DRAFT", 1));

        mockMvc.perform(get("/api/templates/versions/9/structure-mapping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.items[0].role").value("TITLE"));
    }

    @Test
    void savesDraftMapping() throws Exception {
        when(service.saveDraft(eq(9L), any(SaveStructureMappingRequest.class)))
                .thenReturn(profile("DRAFT", 1));

        mockMvc.perform(put("/api/templates/versions/9/structure-mapping/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "nodeKey": "title-node",
                                      "role": "TITLE",
                                      "slotKey": "title",
                                      "status": "CONFIRMED",
                                      "source": "USER",
                                      "confidence": 1,
                                      "notes": "",
                                      "sortOrder": 10
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mappingProfileId").value(1))
                .andExpect(jsonPath("$.data.confirmedCount").value(1));

        verify(service).saveDraft(eq(9L), any(SaveStructureMappingRequest.class));
    }

    @Test
    void publishesMapping() throws Exception {
        when(service.publish(eq(9L), any(PublishStructureMappingRequest.class)))
                .thenReturn(profile("PUBLISHED", 2));

        mockMvc.perform(post("/api/templates/versions/9/structure-mapping/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminOverride\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.versionNo").value(2));
    }

    private StructureMappingProfile profile(String status, int versionNo) {
        return new StructureMappingProfile(
                1L,
                9L,
                versionNo,
                status,
                List.of(new StructureMappingItem("title-node", "TITLE", "title", "CONFIRMED", "USER", 1, "", 10)),
                List.of(),
                0,
                0,
                "PUBLISHED".equals(status) ? Instant.now() : null,
                Instant.now(),
                Instant.now()
        );
    }
}
