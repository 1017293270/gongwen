package com.gongwen.assistant.template.profile;

public record TemplatePlaceholderProfile(
        String key,
        String locationType,
        String paragraphKey,
        String styleId,
        String styleName,
        boolean splitAcrossRuns
) {
}
