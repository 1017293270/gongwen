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

    public static byte[] docxWithComplexTable() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("事项");
            table.getRow(0).getCell(1).setText("{{标题}}");
            table.getRow(1).getCell(0).setText("正文摘要");
            table.getRow(1).getCell(1).setText("{{正文}}");
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create complex table docx", exception);
        }
    }

    public static byte[] docxWithHeaderAndFooter() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createHeader(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("内部资料");
            document.createFooter(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("第  页");
            document.createParagraph().createRun().setText("{{正文}}");
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create header/footer docx", exception);
        }
    }

    public static byte[] docxWithMissingFontFormatting() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            title.createRun().setText("{{标题}}");

            XWPFParagraph body = document.createParagraph();
            body.setIndentationFirstLine(420);
            body.createRun().setText("{{正文}}");

            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create missing font docx", exception);
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

    public static byte[] speechReferenceDocument() {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createHeader(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("内部测试资料");
            document.createFooter(HeaderFooterType.DEFAULT)
                    .createParagraph()
                    .createRun()
                    .setText("测试文档 | 讲话稿范文示例");

            speechParagraph(document, "在全区重点工作推进会上的讲话", ParagraphAlignment.CENTER, 22, true, 0);
            speechParagraph(document, "政务会议讲话稿测试样例", ParagraphAlignment.CENTER, 13, false, 0);
            speechParagraph(document, "2026年5月30日", ParagraphAlignment.CENTER, 12, false, 0);
            speechParagraph(document, "同志们：", ParagraphAlignment.LEFT, 15, false, 0);
            speechParagraph(document, "今天我们召开这次重点工作推进会，主要任务是深入贯彻上级决策部署，全面梳理当前工作进展，分析存在问题，安排下一阶段重点任务，动员全区上下进一步统一思想、压实责任、狠抓落实，以更加扎实的作风推动各项工作取得新成效。", ParagraphAlignment.LEFT, 15, false, 420);
            speechParagraph(document, "今年以来，各部门各单位围绕中心、服务大局，主动担当、协同发力，在项目建设、民生保障、基层治理、营商环境优化等方面做了大量工作，整体态势稳中有进、持续向好。成绩值得肯定，但也要清醒看到，对照高质量发展的要求，对照群众的新期待，我们在工作统筹、执行效率、闭环管理、服务质效等方面仍有差距，需要在下一步工作中认真研究、切实改进。", ParagraphAlignment.LEFT, 15, false, 420);
            speechParagraph(document, "一、提高政治站位，把思想和行动统一到重点任务落实上来", ParagraphAlignment.LEFT, 16, true, 0);
            speechParagraph(document, "抓落实是检验干部作风和治理能力的重要标尺。越是任务繁重、矛盾交织，越要保持清醒和战略定力，把上级要求、发展需要和群众期盼贯通起来，把工作摆到全局中审视、放到实践中检验。", ParagraphAlignment.LEFT, 15, false, 420);
            speechParagraph(document, "要强化系统观念。各项重点工作不是孤立推进的单项任务，而是相互关联、相互支撑的整体工程。要坚持全区一盘棋，加强横向协同和纵向联动，做到目标同向、措施同频、责任同担，避免各管一段、各说各话。", ParagraphAlignment.LEFT, 15, false, 420);
            speechParagraph(document, "二、聚焦关键环节，以务实举措推动工作提质增效", ParagraphAlignment.LEFT, 16, true, 0);
            speechParagraph(document, "下一阶段，要把精力集中到最关键、最紧迫、最能带动全局的工作上来，既抓当前进度，也抓长远质效，确保各项部署落到实处、见到实效。", ParagraphAlignment.LEFT, 15, false, 420);
            speechParagraph(document, "1. 突出项目牵引。坚持把项目建设作为稳增长、促发展的重要支撑，完善项目清单、责任清单、问题清单。", ParagraphAlignment.LEFT, 14, false, 210);
            speechParagraph(document, "2. 优化政务服务。持续改进窗口服务、线上办理和跨部门协同机制，让服务更有温度、办事更有速度。", ParagraphAlignment.LEFT, 14, false, 210);
            speechParagraph(document, "3. 守牢民生底线。紧盯就业、教育、医疗、养老、住房保障等重点领域，把群众急难愁盼事项办实办细。", ParagraphAlignment.LEFT, 14, false, 210);
            speechParagraph(document, "三、压紧压实责任，形成齐抓共管的工作合力", ParagraphAlignment.LEFT, 16, true, 0);
            speechParagraph(document, "责任落实到位，工作才能推进到位。各部门各单位要把职责摆进去、把任务领回去、把压力传导下去，形成一级抓一级、层层抓落实的工作格局。", ParagraphAlignment.LEFT, 15, false, 420);
            speechParagraph(document, "要健全闭环机制。对会议明确的事项，要建立台账、动态更新、销号管理，做到任务有清单、推进有节点、结果有反馈。督查考核要突出实绩导向，既看完成了什么，也看解决了什么问题、带来了什么变化。", ParagraphAlignment.LEFT, 15, false, 420);
            speechParagraph(document, "结束语", ParagraphAlignment.LEFT, 16, true, 0);
            speechParagraph(document, "同志们，做好下一阶段工作，任务艰巨、责任重大。希望大家以更加坚定的信心、更加务实的举措、更加过硬的作风，凝心聚力、攻坚克难，确保各项重点工作高质量推进，为全区经济社会发展提供更加坚实的支撑。", ParagraphAlignment.LEFT, 15, false, 420);
            speechParagraph(document, "我就讲这些，谢谢大家。", ParagraphAlignment.LEFT, 15, false, 420);

            XWPFTable table = document.createTable(3, 2);
            table.getRow(0).getCell(0).setText("文档类型");
            table.getRow(0).getCell(1).setText("政务会议讲话稿（测试样例）");
            table.getRow(1).getCell(0).setText("适用场景");
            table.getRow(1).getCell(1).setText("重点工作推进会、季度调度会、专题部署会");
            table.getRow(2).getCell(0).setText("使用说明");
            table.getRow(2).getCell(1).setText("虚构内容，仅供项目功能、排版、导出和检索测试使用");

            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create speech reference docx", exception);
        }
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

    private static XWPFParagraph speechParagraph(
            XWPFDocument document,
            String text,
            ParagraphAlignment alignment,
            int fontSize,
            boolean bold,
            int firstLineIndent
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingBetween(28.0, LineSpacingRule.EXACT);
        if (firstLineIndent > 0) {
            paragraph.setIndentationFirstLine(firstLineIndent);
        }
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Times New Roman", XWPFRun.FontCharRange.ascii);
        run.setFontFamily("Times New Roman", XWPFRun.FontCharRange.hAnsi);
        run.setFontFamily(fontSize >= 16 && bold ? "方正小标宋简体" : "仿宋_GB2312", XWPFRun.FontCharRange.eastAsia);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setText(text);
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
