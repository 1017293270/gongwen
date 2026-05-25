package com.gongwen.assistant.exporting;

import java.util.Map;

public record WordExportRequest(
        String templateName,
        int templateVersion,
        Map<String, String> values
) {
}
