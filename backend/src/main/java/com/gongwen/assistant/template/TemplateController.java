package com.gongwen.assistant.template;

import com.gongwen.assistant.common.api.ApiResponse;
import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final TemplateUploadService uploadService;
    private final TemplateProfileRepository profileRepository;

    public TemplateController(
            DocxPlaceholderParser parser,
            TemplateUploadService uploadService,
            TemplateProfileRepository profileRepository
    ) {
        this.parser = parser;
        this.uploadService = uploadService;
        this.profileRepository = profileRepository;
    }

    @PostMapping("/parse")
    public ApiResponse<TemplateParseResponse> parse(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(new TemplateParseResponse(parser.parsePlaceholders(file.getBytes())));
    }

    @PostMapping("/{templateId}/versions")
    public ApiResponse<TemplateUploadResponse> uploadVersion(
            @PathVariable long templateId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        return ApiResponse.ok(uploadService.upload(
                templateId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        ));
    }

    @GetMapping("/versions/{versionId}/profile")
    public ApiResponse<TemplateProfile> getProfile(@PathVariable long versionId) {
        return ApiResponse.ok(profileRepository.findByTemplateVersionId(versionId)
                .orElseThrow(() -> new TemplateException("TEMPLATE_PROFILE_NOT_FOUND", "Template profile not found")));
    }

    @ExceptionHandler(TemplateException.class)
    public ResponseEntity<ApiResponse<Void>> handleTemplateException(TemplateException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }

    public record TemplateParseResponse(List<String> placeholders) {
    }
}
