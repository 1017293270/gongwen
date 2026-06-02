package com.gongwen.assistant.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommonApiExceptionHandlerTest.FailingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CommonApiExceptionHandler.class, CommonApiExceptionHandlerTest.FailingController.class})
class CommonApiExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsUserFacingMessageForDataIntegrityFailures() throws Exception {
        mockMvc.perform(get("/test/failing/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.message").value("当前操作与已有数据存在关联，无法完成；请检查关联数据后重试"));
    }

    @Test
    void returnsUserFacingMessageForUnknownFailures() throws Exception {
        mockMvc.perform(get("/test/failing/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("系统暂时无法完成操作，请稍后重试或联系管理员"));
    }

    @Test
    void returnsUserFacingMessageForInvalidRequestBodies() throws Exception {
        mockMvc.perform(post("/test/failing/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求参数有误，请检查后重试"));
    }

    @RestController
    public static class FailingController {
        @GetMapping("/test/failing/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException("foreign key detail that should not leak");
        }

        @GetMapping("/test/failing/unknown")
        void unknown() {
            throw new RuntimeException("sensitive internal detail");
        }

        @PostMapping("/test/failing/validation")
        void validation(@Valid @RequestBody ValidationRequest request) {
        }
    }

    record ValidationRequest(@NotBlank String name) {
    }
}
