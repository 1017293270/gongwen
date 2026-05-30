package com.gongwen.assistant.template;

import com.gongwen.assistant.common.api.ApiResponse;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
import com.gongwen.assistant.template.parser.DocxPlaceholderParser;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final DocxPlaceholderParser parser;
    private final TemplateUploadService uploadService;
    private final TemplateProfileRepository profileRepository;
    private final TemplateStructureFormattingRepository structureFormattingRepository;
    private final TemplateVersionRepository versionRepository;
    private final TemplateRepository templateRepository;
    private final CurrentUserProvider currentUserProvider;

    public TemplateController(
            DocxPlaceholderParser parser,
            TemplateUploadService uploadService,
            TemplateProfileRepository profileRepository,
            TemplateStructureFormattingRepository structureFormattingRepository,
            TemplateVersionRepository versionRepository,
            TemplateRepository templateRepository,
            CurrentUserProvider currentUserProvider
    ) {
        this.parser = parser;
        this.uploadService = uploadService;
        this.profileRepository = profileRepository;
        this.structureFormattingRepository = structureFormattingRepository;
        this.versionRepository = versionRepository;
        this.templateRepository = templateRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/parse")
    public ApiResponse<TemplateParseResponse> parse(@RequestPart("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(new TemplateParseResponse(parser.parsePlaceholders(file.getBytes())));
    }

    @GetMapping
    public ApiResponse<List<TemplateSummary>> listTemplates(@RequestParam(required = false) String documentTypeCode) {
        return ApiResponse.ok(templateRepository.findAll(documentTypeCode, currentUser()));
    }

    @PostMapping
    public ApiResponse<TemplateSummary> createTemplate(@org.springframework.web.bind.annotation.RequestBody CreateTemplateRequest request) {
        return ApiResponse.ok(templateRepository.create(request.templateName(), request.documentTypeCode(), currentUser()));
    }

    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> deleteTemplate(@PathVariable long templateId) {
        templateRepository.deleteById(templateId, currentUser());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{templateId}/versions")
    public ApiResponse<TemplateUploadResponse> uploadVersion(
            @PathVariable long templateId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        templateRepository.findById(templateId, currentUser())
                .orElseThrow(() -> new TemplateException("TEMPLATE_NOT_FOUND", "Template not found"));
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

    @GetMapping("/versions/{versionId}/structure-formatting")
    public ApiResponse<Map<String, TemplateStructureFormattingProfile>> getStructureFormatting(@PathVariable long versionId) {
        return ApiResponse.ok(structureFormattingRepository.findOverrides(versionId));
    }

    @PutMapping("/versions/{versionId}/structures/{structureKey}/formatting")
    public ApiResponse<TemplateStructureFormattingProfile> updateStructureFormatting(
            @PathVariable long versionId,
            @PathVariable String structureKey,
            @RequestBody TemplateStructureFormattingProfile formatting
    ) {
        structureFormattingRepository.saveOverride(versionId, structureKey, formatting);
        return ApiResponse.ok(formatting);
    }

    @GetMapping("/versions")
    public ApiResponse<List<TemplateVersionSummary>> listReadyVersions(
            @RequestParam(required = false) String documentTypeCode
    ) {
        return ApiResponse.ok(versionRepository.findReadyVersions(documentTypeCode, currentUser()));
    }

    private CurrentUser currentUser() {
        return currentUserProvider.currentUser();
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
