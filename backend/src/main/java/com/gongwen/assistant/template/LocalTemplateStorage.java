package com.gongwen.assistant.template;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Component
public class LocalTemplateStorage implements TemplateStorage {
    private final TemplateProperties properties;

    public LocalTemplateStorage(TemplateProperties properties) {
        this.properties = properties;
    }

    @Override
    public String save(String originalFileName, String fileExtension, byte[] content) throws IOException {
        Path directory = Path.of(properties.storageDir()).normalize();
        Files.createDirectories(directory);
        String safeBaseName = StringUtils.cleanPath(originalFileName)
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replace("..", "_");
        String fileName = Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + safeBaseName;
        if (!fileName.endsWith("." + fileExtension)) {
            fileName = fileName + "." + fileExtension;
        }
        Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw new IOException("Invalid template storage path");
        }
        Files.write(target, content);
        return target.toString();
    }
}
