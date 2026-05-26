package com.gongwen.assistant.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class MockModelAdapter implements ModelAdapter {
    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public String modelName() {
        return "mock-outline-v1";
    }

    @Override
    public AiOutlineResponse generateOutline(OutlinePrompt prompt) {
        List<AiOutlineSection> sections = new ArrayList<>();
        sections.add(new AiOutlineSection("一、背景与依据", List.of("概括发文背景", "结合材料提炼主要依据")));
        sections.add(new AiOutlineSection("二、主要事项", List.of("明确工作安排", "说明责任分工和时间要求")));
        sections.add(new AiOutlineSection("三、执行要求", List.of("提出落实要求", "补充报送或反馈方式")));

        List<String> missing = new ArrayList<>();
        if (isBlankField(prompt, "RECIPIENT")) {
            missing.add("主送对象");
        }
        if (prompt.materialSummaries().isEmpty()) {
            missing.add("支撑材料");
        }
        if (prompt.instruction().isBlank()) {
            missing.add("补充写作要求");
        }

        return new AiOutlineResponse(
                UUID.randomUUID(),
                prompt.title().isBlank() ? "关于有关事项的通知" : prompt.title(),
                sections,
                missing
        );
    }

    @Override
    public AiParagraphModelResponse generateParagraph(ParagraphPrompt prompt) {
        String points = prompt.points().isEmpty()
                ? "围绕" + prompt.heading() + "展开具体说明"
                : String.join("；", prompt.points());
        String instruction = prompt.instruction().isBlank() ? "" : prompt.instruction();
        return new AiParagraphModelResponse(prompt.heading() + "：" + points + "。" + instruction);
    }

    private boolean isBlankField(OutlinePrompt prompt, String blockType) {
        return prompt.fieldSummaries().stream()
                .filter(summary -> summary.startsWith(blockType + ":"))
                .allMatch(summary -> summary.equals(blockType + ": "));
    }
}
