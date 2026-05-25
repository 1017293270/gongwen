package com.gongwen.assistant.template;

import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemplateConfiguration {
    @Bean
    DocxPlaceholderParser docxPlaceholderParser() {
        return new DocxPlaceholderParser();
    }
}
