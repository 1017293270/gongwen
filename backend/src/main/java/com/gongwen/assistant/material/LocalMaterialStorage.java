package com.gongwen.assistant.material;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Component
public class LocalMaterialStorage implements MaterialStorage {
    private final MaterialProperties properties;

    public LocalMaterialStorage(MaterialProperties properties) {
        this.properties = properties;
    }

    @Override
    public String save(long draftId, String originalFileName, String fileExtension, byte[] content) throws IOException {
        Path draftDirectory = Path.of(properties.storageDir(), "draft-" + draftId).normalize();
        Files.createDirectories(draftDirectory);
        String safeBaseName = StringUtils.cleanPath(originalFileName).replaceAll("[^A-Za-z0-9._-]", "_");
        String fileName = Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "-" + safeBaseName;
        if (!fileName.endsWith("." + fileExtension)) {
            fileName = fileName + "." + fileExtension;
        }
        Path target = draftDirectory.resolve(fileName).normalize();
        if (!target.startsWith(draftDirectory)) {
            throw new IOException("Invalid material storage path");
        }
        Files.write(target, content);
        return target.toString();
    }
}
