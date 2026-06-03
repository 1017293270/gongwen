package com.gongwen.assistant.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekModelAdapterParsingTest {

    @Test
    void extractsJsonFromMarkdownFence() {
        String content = """
                ```json
                {"title":"test","sections":[]}
                ```
                """;

        assertThat(DeepSeekModelAdapter.extractJsonObject(content))
                .isEqualTo("{\"title\":\"test\",\"sections\":[]}");
    }

    @Test
    void extractsFirstJsonObjectFromExplanatoryText() {
        String content = "Here is the result:\n{\"content\":\"value with } inside text\"}\nDone.";

        assertThat(DeepSeekModelAdapter.extractJsonObject(content))
                .isEqualTo("{\"content\":\"value with } inside text\"}");
    }

    @Test
    void rejectsResponseWithoutJsonObject() {
        assertThatThrownBy(() -> DeepSeekModelAdapter.extractJsonObject("not json"))
                .isInstanceOf(ModelAdapterException.class)
                .hasMessage("DeepSeek 返回结构无效");
    }
}
