package com.gongwen.assistant.material;

import com.gongwen.assistant.draft.DraftService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MaterialService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("docx", "pdf");
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "pdf", Set.of("application/pdf")
    );

    private final DraftService draftService;
    private final MaterialRepository materialRepository;
    private final MaterialStorage materialStorage;
    private final MaterialTextExtractor materialTextExtractor;
    private final MaterialProperties properties;

    public MaterialService(
            DraftService draftService,
            MaterialRepository materialRepository,
            MaterialStorage materialStorage,
            MaterialTextExtractor materialTextExtractor,
            MaterialProperties properties
    ) {
        this.draftService = draftService;
        this.materialRepository = materialRepository;
        this.materialStorage = materialStorage;
        this.materialTextExtractor = materialTextExtractor;
        this.properties = properties;
    }

    public MaterialDto uploadMaterial(long draftId, MultipartFile file) {
        draftService.getDraft(draftId);
        validateFile(file);

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
        String fileExtension = extensionOf(originalFileName);
        byte[] content = readContent(file);
        String storagePath = saveFile(draftId, originalFileName, fileExtension, content);

        try {
            String extractedText = materialTextExtractor.extract(fileExtension, content);
            return materialRepository.save(new MaterialSaveCommand(
                    draftId,
                    originalFileName,
                    file.getContentType(),
                    file.getSize(),
                    fileExtension,
                    storagePath,
                    "READY",
                    extractedText,
                    null
            ));
        } catch (MaterialExtractionException exception) {
            return materialRepository.save(new MaterialSaveCommand(
                    draftId,
                    originalFileName,
                    file.getContentType(),
                    file.getSize(),
                    fileExtension,
                    storagePath,
                    "FAILED",
                    null,
                    exception.getMessage()
            ));
        }
    }

    public List<MaterialDto> listMaterials(long draftId) {
        draftService.getDraft(draftId);
        return materialRepository.findByDraftId(draftId);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MaterialUploadException("MATERIAL_FILE_EMPTY", "材料文件不能为空");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new MaterialUploadException("MATERIAL_FILE_TOO_LARGE", "材料文件不能超过 20MB");
        }
        String originalFileName = file.getOriginalFilename();
        String fileExtension = extensionOf(originalFileName);
        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new MaterialUploadException("MATERIAL_TYPE_NOT_ALLOWED", "仅支持上传 Word 或 PDF 材料");
        }
        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType) && !ALLOWED_CONTENT_TYPES.get(fileExtension).contains(contentType)) {
            throw new MaterialUploadException("MATERIAL_TYPE_NOT_ALLOWED", "仅支持上传 Word 或 PDF 材料");
        }
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new MaterialUploadException("MATERIAL_FILE_READ_FAILED", "读取材料文件失败");
        }
    }

    private String saveFile(long draftId, String originalFileName, String fileExtension, byte[] content) {
        try {
            return materialStorage.save(draftId, originalFileName, fileExtension, content);
        } catch (IOException exception) {
            throw new MaterialUploadException("MATERIAL_STORAGE_FAILED", "保存材料文件失败");
        }
    }

    private String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
