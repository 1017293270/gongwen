package com.gongwen.assistant.material;

import com.gongwen.assistant.common.api.ApiResponse;
import com.gongwen.assistant.draft.DraftNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/drafts/{draftId}/materials")
public class MaterialController {
    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping
    public ApiResponse<MaterialDto> uploadMaterial(@PathVariable long draftId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(materialService.uploadMaterial(draftId, file));
    }

    @GetMapping
    public ApiResponse<List<MaterialDto>> listMaterials(@PathVariable long draftId) {
        return ApiResponse.ok(materialService.listMaterials(draftId));
    }

    @ExceptionHandler(MaterialUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaterialUpload(MaterialUploadException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDraftNotFound(DraftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("DRAFT_NOT_FOUND", exception.getMessage()));
    }
}
