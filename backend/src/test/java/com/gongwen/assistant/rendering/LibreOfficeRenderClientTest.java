package com.gongwen.assistant.rendering;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LibreOfficeRenderClientTest {
    @TempDir
    Path tempDir;

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

    @Test
    void reportsUnavailableLibreOfficeEnvironment() {
        LibreOfficeRenderClient client = new LibreOfficeRenderClient(new RenderPreviewProperties(
                "storage/previews",
                "libreoffice",
                "Z:/missing/soffice.exe",
                180,
                20
        ));

        RenderPreviewEnvironmentStatus status = client.environmentStatus();

        assertThat(status.available()).isFalse();
        assertThat(status.libreOfficePath()).isEqualTo("Z:/missing/soffice.exe");
        assertThat(status.message()).contains("GONGWEN_LIBREOFFICE_PATH");
    }

    @Test
    void resolvesDefaultSofficeFromKnownInstallPath() throws IOException {
        Path installedSoffice = Files.createDirectories(tempDir.resolve("LibreOffice/program"))
                .resolve("soffice.exe");
        Files.writeString(installedSoffice, "");
        LibreOfficeRenderClient client = new LibreOfficeRenderClient(new RenderPreviewProperties(
                "storage/previews",
                "libreoffice",
                "soffice",
                180,
                20
        ), List.of(installedSoffice));

        RenderPreviewEnvironmentStatus status = client.environmentStatus();
        List<String> command = client.buildCommand(Path.of("C:/templates/notice.docx"), Path.of("C:/previews/version-1"));

        assertThat(status.available()).isTrue();
        assertThat(status.libreOfficePath()).isEqualTo(installedSoffice.toString());
        assertThat(command.getFirst()).isEqualTo(installedSoffice.toString());
    }

    @Test
    void readsLibreOfficeLogWithNonUtf8BytesLossily() throws IOException {
        LibreOfficeRenderClient client = new LibreOfficeRenderClient(new RenderPreviewProperties(
                "storage/previews",
                "libreoffice",
                "soffice",
                180,
                20
        ));
        Path logFile = tempDir.resolve("libreoffice-render.log");
        Files.write(logFile, new byte[]{(byte) 0xd0, (byte) 0xc2, (byte) 0xca, (byte) 0xd4});

        String output = client.readProcessLog(logFile);

        assertThat(output).isNotBlank();
    }
}
