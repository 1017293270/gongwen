package com.gongwen.assistant.material;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/drafts/{draftId}/materials")
@PreAuthorize("hasAnyRole('DRAFTER', 'TEMPLATE_ADMIN', 'SYSTEM_ADMIN')")
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
}
