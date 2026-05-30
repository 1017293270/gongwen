package com.gongwen.assistant.rendering;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LibreOfficeRenderClientTest {
    @Test
    void buildsHeadlessPdfConversionCommand() {
        LibreOfficeRenderClient client = new LibreOfficeRenderClient(new RenderPreviewProperties(
                "storage/previews",
                "libreoffice",
                "C:/Program Files/LibreOffice/program/soffice.exe",
                180,
                20
        ));

        Path input = Path.of("C:/templates/notice.docx");
        Path output = Path.of("C:/previews/version-1");
        List<String> command = client.buildCommand(input, output);

        assertThat(command).containsExactly(
                "C:/Program Files/LibreOffice/program/soffice.exe",
                "--headless",
                "--convert-to",
                "pdf",
                "--outdir",
                output.toString(),
                input.toString()
        );
    }
}
