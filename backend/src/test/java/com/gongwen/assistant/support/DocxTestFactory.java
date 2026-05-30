package com.gongwen.assistant.support;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;

public final class DocxTestFactory {
    private DocxTestFactory() {
    }

    public static byte[] docxWithParagraphs(String... paragraphs) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String paragraphText : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(paragraphText);
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create test docx", exception);
        }
    }

    public static byte[] docxWithTableCell(String cellText) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFTable table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText(cellText);
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create test docx table", exception);
        }
    }

    public static byte[] docxWithSplitPlaceholder() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText("{{标");
            paragraph.createRun().setText("题}}");
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create split placeholder docx", exception);
        }
    }

    public static byte[] docxWithOfficialStyles() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("official_title");
            title.createRun().setText("{{标题}}");

            XWPFParagraph body = document.createParagraph();
            body.setStyle("body_text");
            body.createRun().setText("{{正文}}");

            document.createHeader(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("机关公文");

            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create styled docx", exception);
        }
    }

    public static byte[] docxWithOfficialStyleFormatting() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("official_title");
            title.setAlignment(ParagraphAlignment.CENTER);
            title.setSpacingBefore(240);
            title.setSpacingAfter(120);
            XWPFRun titleRun = title.createRun();
            titleRun.setFontFamily("SimHei");
            titleRun.setFontSize(22);
            titleRun.setBold(true);
            titleRun.setText("{{标题}}");

            XWPFParagraph body = document.createParagraph();
            body.setStyle("body_text");
            body.setAlignment(ParagraphAlignment.BOTH);
            body.setIndentationFirstLine(420);
            body.setSpacingBetween(1.5);
            XWPFRun bodyRun = body.createRun();
            bodyRun.setFontFamily("FangSong");
            bodyRun.setFontSize(16);
            bodyRun.setText("{{正文}}");

            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create formatted style docx", exception);
        }
    }

    public static byte[] docxWithNoticeReferenceFormatting() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph unit = document.createParagraph();
            unit.setStyle("GongwenUnit");
            unit.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun unitRun = unit.createRun();
            unitRun.setFontFamily("SimSun");
            unitRun.setFontSize(18);
            unitRun.setBold(true);
            unitRun.setText("示例单位文件");

            XWPFParagraph meta = document.createParagraph();
            meta.setStyle("GongwenMeta");
            meta.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun metaRun = meta.createRun();
            metaRun.setFontFamily("FangSong");
            metaRun.setFontSize(14);
            metaRun.setText("示例办〔2026〕5号");

            XWPFParagraph title = document.createParagraph();
            title.setStyle("GongwenTitle");
            title.setAlignment(ParagraphAlignment.CENTER);
            title.setSpacingAfter(360);
            XWPFRun titleRun = title.createRun();
            titleRun.setFontFamily("SimSun");
            titleRun.setFontSize(22);
            titleRun.setBold(true);
            titleRun.setText("关于召开2026年第二季度行政办公例会的通知");

            XWPFParagraph recipient = bodyParagraph(document, 0, 120, ParagraphAlignment.LEFT);
            recipient.createRun().setText("各部门、各直属单位：");

            XWPFParagraph intro = bodyParagraph(document, 635, 0, ParagraphAlignment.LEFT);
            intro.createRun().setText("为统筹推进近期重点工作，现将有关事项通知如下：");

            XWPFParagraph heading = bodyParagraph(document, 635, 0, ParagraphAlignment.LEFT);
            heading.createRun().setText("一、会议时间");

            XWPFParagraph attachment = bodyParagraph(document, 635, 480, ParagraphAlignment.LEFT);
            attachment.createRun().setText("附件：会议议题征集表");

            XWPFParagraph signature = bodyParagraph(document, 0, 0, ParagraphAlignment.RIGHT);
            signature.createRun().setText("示例单位办公室");

            XWPFParagraph date = bodyParagraph(document, 0, 0, ParagraphAlignment.RIGHT);
            date.createRun().setText("2026年5月27日");

            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create notice reference docx", exception);
        }
    }

    public static byte[] docxWithEastAsiaAndLatinFonts() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph body = document.createParagraph();
            XWPFRun run = body.createRun();
            run.setFontFamily("Times New Roman", XWPFRun.FontCharRange.ascii);
            run.setFontFamily("Times New Roman", XWPFRun.FontCharRange.hAnsi);
            run.setFontFamily("FangSong", XWPFRun.FontCharRange.eastAsia);
            run.setFontSize(16);
            run.setText("{{正文}}ABC");

            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create eastAsia font docx", exception);
        }
    }

    public static byte[] docxWithStructuredLineSpacing() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setSpacingBetween(29.5, LineSpacingRule.EXACT);
            title.createRun().setText("{{标题}}");

            XWPFParagraph body = document.createParagraph();
            body.setSpacingBetween(1.5, LineSpacingRule.AUTO);
            body.createRun().setText("{{正文}}");

            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create structured line spacing docx", exception);
        }
    }

    public static byte[] docxWithManualGuideLikeDocument() {
        return docxWithParagraphs(
                "高新发展公文使用手册",
                "一、公文格式说明",
                "1.标题：方正小标宋简体（二号）",
                "2.正文：方正仿宋三号，首行缩进2字符",
                "3.附件：附件说明应位于正文之后",
                "本手册用于说明通知、请示、报告等文种的写作规范。"
        );
    }

    public static byte[] docxWithNoticeReferenceSkeleton() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CTSectPr section = document.getDocument().getBody().isSetSectPr()
                    ? document.getDocument().getBody().getSectPr()
                    : document.getDocument().getBody().addNewSectPr();
            section.addNewPgSz().setW(BigInteger.valueOf(11906));
            section.getPgSz().setH(BigInteger.valueOf(16838));
            section.addNewPgMar().setTop(BigInteger.valueOf(2098));
            section.getPgMar().setRight(BigInteger.valueOf(1474));
            section.getPgMar().setBottom(BigInteger.valueOf(1984));
            section.getPgMar().setLeft(BigInteger.valueOf(1587));
            section.getPgMar().setHeader(BigInteger.valueOf(850));
            section.getPgMar().setFooter(BigInteger.valueOf(992));

            document.createFooter(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("-  -");

            XWPFParagraph unit = document.createParagraph();
            unit.setStyle("GongwenUnit");
            unit.setAlignment(ParagraphAlignment.CENTER);
            unit.setSpacingAfter(160);
            XWPFRun unitRun = unit.createRun();
            unitRun.setFontFamily("SimSun");
            unitRun.setFontSize(18);
            unitRun.setBold(true);
            unitRun.setColor("C00000");
            unitRun.setText("示例单位文件");

            XWPFParagraph meta = document.createParagraph();
            meta.setStyle("GongwenMeta");
            meta.setAlignment(ParagraphAlignment.CENTER);
            meta.setSpacingAfter(440);
            XWPFRun metaRun = meta.createRun();
            metaRun.setFontFamily("FangSong");
            metaRun.setFontSize(14);
            metaRun.setText("示例办〔2026〕5号");

            XWPFParagraph title = document.createParagraph();
            title.setStyle("GongwenTitle");
            title.setAlignment(ParagraphAlignment.CENTER);
            title.setSpacingAfter(360);
            XWPFRun titleRun = title.createRun();
            titleRun.setFontFamily("SimSun");
            titleRun.setFontSize(22);
            titleRun.setBold(true);
            titleRun.setText("关于召开2026年第二季度行政办公例会的通知");

            XWPFParagraph recipient = bodyParagraph(document, 0, 120, ParagraphAlignment.LEFT);
            recipient.createRun().setText("各部门、各直属单位：");

            XWPFParagraph intro = bodyParagraph(document, 635, 0, ParagraphAlignment.LEFT);
            intro.createRun().setText("为统筹推进近期重点工作，现将有关事项通知如下：");

            XWPFParagraph heading = bodyParagraph(document, 635, 0, ParagraphAlignment.LEFT);
            heading.createRun().setText("一、会议时间");

            XWPFParagraph attachment = bodyParagraph(document, 635, 480, ParagraphAlignment.LEFT);
            attachment.createRun().setText("附件：会议议题征集表");

            XWPFParagraph signature = bodyParagraph(document, 0, 0, ParagraphAlignment.RIGHT);
            signature.createRun().setText("示例单位办公室");

            XWPFParagraph date = bodyParagraph(document, 0, 0, ParagraphAlignment.RIGHT);
            date.createRun().setText("2026年5月27日");

            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create notice reference skeleton docx", exception);
        }
    }

    private static XWPFParagraph bodyParagraph(
            XWPFDocument document,
            int firstLineIndent,
            int spacingAfter,
            ParagraphAlignment alignment
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("GongwenBody");
        paragraph.setAlignment(alignment);
        paragraph.setIndentationFirstLine(firstLineIndent);
        paragraph.setSpacingAfter(spacingAfter);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("FangSong");
        run.setFontSize(16);
        return paragraph;
    }

    public static String readText(byte[] docxBytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> text.append(paragraph.getText()).append('\n'));
            document.getTables().forEach(table -> table.getRows().forEach(row -> row.getTableCells()
                    .forEach(cell -> text.append(cell.getText()).append('\n'))));
            return text.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read test docx", exception);
        }
    }
}
