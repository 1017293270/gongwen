package com.gongwen.assistant.template;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateUploadServiceTest {
    @Test
    void localStorageSavesTemplateUnderConfiguredDirectory() throws IOException {
        Path storageDir = Files.createTempDirectory("template-storage-test");
        LocalTemplateStorage storage = new LocalTemplateStorage(new TemplateProperties(storageDir.toString(), 20));

        String path = storage.save("notice-template.docx", "docx", "template".getBytes());

        assertThat(Path.of(path)).startsWith(storageDir);
        assertThat(Files.readString(Path.of(path))).isEqualTo("template");
        assertThat(path).endsWith(".docx");
    }
}
