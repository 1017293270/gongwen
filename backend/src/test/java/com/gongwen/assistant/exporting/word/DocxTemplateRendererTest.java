package com.gongwen.assistant.exporting.word;

import com.gongwen.assistant.support.DocxTestFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxTemplateRendererTest {
    private final DocxTemplateRenderer renderer = new DocxTemplateRenderer();

    @Test
    void replacesPlaceholdersAndPreservesLineBreaks() {
        byte[] template = DocxTestFactory.docxWithParagraphs(
                "{{标题}}",
                "{{主送}}：",
                "{{正文}}",
                "{{落款}}",
                "{{日期}}"
        );

        byte[] exported = renderer.render(template, Map.of(
                "标题", "关于开展年度档案整理工作的通知",
                "主送", "各部门、各直属单位",
                "正文", "第一段内容\n第二段内容",
                "落款", "办公室",
                "日期", "2026年5月25日"
        ));

        String text = DocxTestFactory.readText(exported);
        assertThat(text).contains("关于开展年度档案整理工作的通知");
        assertThat(text).contains("各部门、各直属单位：");
        assertThat(text).contains("第一段内容");
        assertThat(text).contains("第二段内容");
        assertThat(text).contains("办公室");
        assertThat(text).doesNotContain("{{标题}}");
    }

    @Test
    void failsWhenAPlaceholderValueIsMissing() {
        byte[] template = DocxTestFactory.docxWithParagraphs("{{标题}}", "{{正文}}");

        assertThatThrownBy(() -> renderer.render(template, Map.of("标题", "测试标题")))
                .isInstanceOf(MissingTemplateValueException.class)
                .hasMessageContaining("正文");
    }
}
