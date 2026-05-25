package com.gongwen.assistant.template.parser;

import java.util.List;

public record ParsedTemplate(List<String> placeholders) {
    public ParsedTemplate {
        placeholders = List.copyOf(placeholders);
    }
}
