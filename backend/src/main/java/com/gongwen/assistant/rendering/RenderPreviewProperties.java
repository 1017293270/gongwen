package com.gongwen.assistant.rendering;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gongwen.render-preview")
public record RenderPreviewProperties(
        String storageDir,
        String renderer,
        String libreOfficePath,
        int dpi,
        int timeoutSeconds
) {
    public RenderPreviewProperties {
        if (storageDir == null || storageDir.isBlank()) {
            storageDir = "storage/previews";
        }
        if (renderer == null || renderer.isBlank()) {
            renderer = "libreoffice";
        }
        if (libreOfficePath == null || libreOfficePath.isBlank()) {
            libreOfficePath = "soffice";
        }
        if (dpi <= 0) {
            dpi = 150;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 60;
        }
    }
}
