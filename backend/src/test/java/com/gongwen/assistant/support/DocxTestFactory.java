package com.gongwen.assistant.support;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.wp.usermodel.HeaderFooterType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
