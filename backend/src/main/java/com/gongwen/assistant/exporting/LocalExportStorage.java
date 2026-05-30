package com.gongwen.assistant.exporting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class LocalExportStorage implements ExportStorage {
    private final Path storageDir;

    public LocalExportStorage(@Value("${gongwen.export.storage-dir:storage/exports}") String storageDir) {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public String save(String fileName, byte[] content) throws IOException {
        Files.createDirectories(storageDir);
        String safeFileName = safeFileName(fileName);
        Path target = storageDir.resolve(UUID.randomUUID() + "-" + safeFileName).normalize();
        if (!target.startsWith(storageDir)) {
            throw new IOException("Invalid export storage path");
        }
        Files.write(target, content);
        return target.toString();
    }

    private String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "export.docx";
        }
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
