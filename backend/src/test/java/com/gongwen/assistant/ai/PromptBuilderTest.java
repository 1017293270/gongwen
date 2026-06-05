package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftDetailDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {
    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void buildsSafeOutlinePromptSummaryWithoutFullMaterialText() {
        String longMaterial = "材料正文".repeat(220);
        DraftDetailDto draft = new DraftDetailDto(1L, "NOTICE", "测试通知", "DRAFT", List.of(
                new DraftBlockDto(1L, "TITLE", "测试通知", 10),
                new DraftBlockDto(2L, "BODY_PARAGRAPH", "请各部门做好材料报送。", 20)
        ));

        OutlinePrompt prompt = promptBuilder.buildOutlinePrompt(
                draft,
                List.of(new MaterialPromptSummary(1L, "meeting.docx", longMaterial)),
                "突出执行要求"
        );

        assertThat(prompt.promptVersion()).isEqualTo("outline-v2");
        assertThat(prompt.documentTypeCode()).isEqualTo("NOTICE");
        assertThat(prompt.fieldSummaries()).contains("TITLE: 测试通知");
        assertThat(prompt.materialSummaries()).hasSize(1);
        assertThat(prompt.materialSummaries().get(0)).startsWith("materialId=1;file=meeting.docx;summary=材料正文");
        assertThat(prompt.materialSummaries().get(0)).doesNotContain(longMaterial);
        assertThat(prompt.inputSummary()).contains("documentType=NOTICE");
        assertThat(prompt.inputSummary()).contains("materials=1");
        assertThat(prompt.inputSummary()).doesNotContain("材料正文");
    }

    @Test
    void buildsNodeAwarePromptSummariesWithoutFullNodeContext() {
        DraftDetailDto draft = new DraftDetailDto(1L, "NOTICE", "测试通知", "DRAFT", List.of(
                new DraftBlockDto(1L, "TITLE", "测试通知", 10),
                new DraftBlockDto(2L, "BODY_PARAGRAPH", "请各部门做好材料报送。", 20)
        ));
        AiNodeContext nodeContext = new AiNodeContext(8L, "BODY", "正文", "节点正文".repeat(80));

        ParagraphPrompt paragraphPrompt = promptBuilder.buildParagraphPrompt(
                draft,
                List.of(),
                new AiParagraphRequest("一、主要事项", List.of("说明安排"), "", 20, 8L, "BODY", "正文", "节点正文"),
                nodeContext
        );
        LocalOperationPrompt localPrompt = promptBuilder.buildLocalOperationPrompt(
                draft,
                List.of(),
                nodeContext,
                20,
                new AiLocalOperationRequest(null, 8L, "BODY", "正文", "节点正文", AiLocalOperationType.REWRITE, "")
        );

        assertThat(paragraphPrompt.inputSummary()).contains("nodeId=8", "nodeRole=BODY", "nodeContextChars=");
        assertThat(paragraphPrompt.inputSummary()).doesNotContain("节点正文节点正文");
        assertThat(paragraphPrompt.nodeContext().promptSummary()).contains("nodeId=8", "nodeRole=BODY");
        assertThat(localPrompt.inputSummary()).contains("targetNodeId=8", "targetNodeRole=BODY");
        assertThat(localPrompt.inputSummary()).doesNotContain("节点正文节点正文");
    }
}
