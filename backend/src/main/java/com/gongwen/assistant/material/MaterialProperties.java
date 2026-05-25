package com.gongwen.assistant.material;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MaterialProperties {
    private final String storageDir;
    private final long maxFileSizeBytes;

    public MaterialProperties(
            @Value("${gongwen.material.storage-dir:storage/materials}") String storageDir,
            @Value("${gongwen.material.max-file-size-bytes:20971520}") long maxFileSizeBytes
    ) {
        this.storageDir = storageDir;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String storageDir() {
        return storageDir;
    }

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }
}
