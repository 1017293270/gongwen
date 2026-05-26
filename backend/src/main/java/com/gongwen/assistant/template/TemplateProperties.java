package com.gongwen.assistant.template;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gongwen.template")
public record TemplateProperties(
        String storageDir,
        long maxUploadMb
) {
    public TemplateProperties {
        if (storageDir == null || storageDir.isBlank()) {
            storageDir = "storage/templates";
        }
        if (maxUploadMb <= 0) {
            maxUploadMb = 20;
        }
    }
}
