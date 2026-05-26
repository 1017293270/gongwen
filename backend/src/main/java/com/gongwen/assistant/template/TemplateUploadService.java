package com.gongwen.assistant.template;

import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileParser;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class TemplateUploadService {
    private static final String DOCX_EXTENSION = ".docx";
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final TemplateStorage storage;
    private final TemplateVersionRepository versionRepository;
    private final TemplateProfileRepository profileRepository;
    private final TemplateProfileParser profileParser;
    private final TemplateProperties properties;

    public TemplateUploadService(
            TemplateStorage storage,
            TemplateVersionRepository versionRepository,
            TemplateProfileRepository profileRepository,
            TemplateProfileParser profileParser,
            TemplateProperties properties
    ) {
        this.storage = storage;
        this.versionRepository = versionRepository;
        this.profileRepository = profileRepository;
        this.profileParser = profileParser;
        this.properties = properties;
    }

    public TemplateUploadResponse upload(long templateId, String originalFileName, String contentType, byte[] content) {
        validate(originalFileName, contentType, content);

        TemplateVersion version = null;
        try {
            String filePath = storage.save(originalFileName, "docx", content);
            version = versionRepository.create(templateId, originalFileName, contentType, content.length, filePath);

            TemplateProfile profile = profileParser.parse(content);
            String profileHash = sha256(content);
            profileRepository.save(version.id(), profile, profileHash);
            versionRepository.markParsed(version.id(), profileHash);

            return new TemplateUploadResponse(
                    version.id(),
                    version.versionNo(),
                    "READY",
                    profile.placeholders().size(),
                    profile.styles().size(),
                    profile.validationItems().size(),
                    profile.validationItems().stream().map(item -> item.code()).toList()
            );
        } catch (IOException exception) {
            throw new TemplateException("TEMPLATE_STORAGE_FAILED", "Template file storage failed", exception);
        } catch (TemplateException exception) {
            markVersionFailed(version, exception);
            throw exception;
        } catch (RuntimeException exception) {
            markVersionFailed(version, exception);
            throw new TemplateException("TEMPLATE_PARSE_FAILED", "Template parsing failed", exception);
        }
    }

    private void validate(String originalFileName, String contentType, byte[] content) {
        if (originalFileName == null || !originalFileName.toLowerCase(Locale.ROOT).endsWith(DOCX_EXTENSION)) {
            throw new TemplateException("TEMPLATE_TYPE_NOT_ALLOWED", "Only .docx Word templates are supported");
        }
        if (!DOCX_CONTENT_TYPE.equals(contentType)) {
            throw new TemplateException("TEMPLATE_CONTENT_TYPE_NOT_ALLOWED", "Only .docx Word template content type is supported");
        }
        if (content == null || content.length == 0) {
            throw new TemplateException("TEMPLATE_FILE_EMPTY", "Template file content is empty");
        }
        long maxBytes = properties.maxUploadMb() * 1024L * 1024L;
        if (content.length > maxBytes) {
            throw new TemplateException("TEMPLATE_FILE_TOO_LARGE", "Template file cannot exceed " + properties.maxUploadMb() + "MB");
        }
    }

    private void markVersionFailed(TemplateVersion version, RuntimeException exception) {
        if (version == null) {
            return;
        }
        versionRepository.markFailed(version.id(), "TEMPLATE_PARSE_FAILED", exception.getMessage());
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
