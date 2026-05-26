package com.gongwen.assistant.template.profile;

public record TemplateSectionProfile(
        int index,
        boolean hasHeader,
        boolean hasFooter,
        Integer pageWidth,
        Integer pageHeight,
        Integer marginTop,
        Integer marginRight,
        Integer marginBottom,
        Integer marginLeft
) {
}
