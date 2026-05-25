package com.gongwen.assistant.template;

import com.gongwen.assistant.common.api.ApiResponse;
import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final DocxPlaceholderParser parser;

    public TemplateController(DocxPlaceholderParser parser) {
        this.parser = parser;
    }

    @PostMapping("/parse")
    public ApiResponse<TemplateParseResponse> parse(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(new TemplateParseResponse(parser.parsePlaceholders(file.getBytes())));
    }

    public record TemplateParseResponse(List<String> placeholders) {
    }
}
