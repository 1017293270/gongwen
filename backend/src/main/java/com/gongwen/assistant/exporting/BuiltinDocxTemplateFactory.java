package com.gongwen.assistant.exporting;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class BuiltinDocxTemplateFactory {
    public byte[] createNoticeTemplate() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addParagraph(document, "{{标题}}");
            addParagraph(document, "{{主送}}：");
            addParagraph(document, "{{正文}}");
            addParagraph(document, "{{附件}}");
            addParagraph(document, "{{落款}}");
            addParagraph(document, "{{日期}}");
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建内置 Word 模板", exception);
        }
    }

    private void addParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }
}
