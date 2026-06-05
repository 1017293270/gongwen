package com.gongwen.assistant.draft.node;

import com.gongwen.assistant.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drafts/{draftId}/nodes")
@PreAuthorize("hasAnyRole('DRAFTER', 'TEMPLATE_ADMIN', 'SYSTEM_ADMIN')")
public class DraftNodeController {
    private final DraftNodeService service;

    public DraftNodeController(DraftNodeService service) {
        this.service = service;
    }

    @PostMapping("/initialize")
    public ApiResponse<List<DraftNodeDto>> initialize(
            @PathVariable long draftId,
            @RequestBody(required = false) InitializeDraftNodesRequest request
    ) {
        return ApiResponse.ok(service.initializeNodes(draftId, request));
    }

    @PostMapping("/reinitialize")
    public ApiResponse<List<DraftNodeDto>> reinitialize(
            @PathVariable long draftId,
            @RequestBody(required = false) ReinitializeDraftNodesRequest request
    ) {
        return ApiResponse.ok(service.reinitializeNodes(draftId, request));
    }

    @GetMapping
    public ApiResponse<List<DraftNodeDto>> list(@PathVariable long draftId) {
        return ApiResponse.ok(service.listNodes(draftId));
    }

    @GetMapping("/insertable-roles")
    public ApiResponse<List<DraftNodeRoleOptionDto>> listInsertableRoles(@PathVariable long draftId) {
        return ApiResponse.ok(service.listInsertableRoles(draftId));
    }

    @PostMapping
    public ApiResponse<List<DraftNodeDto>> insert(
            @PathVariable long draftId,
            @RequestBody(required = false) InsertDraftNodeRequest request
    ) {
        return ApiResponse.ok(service.insertNode(draftId, request));
    }

    @PostMapping("/apply-outline")
    public ApiResponse<ApplyOutlineResponse> applyOutline(
            @PathVariable long draftId,
            @RequestBody(required = false) ApplyOutlineRequest request
    ) {
        return ApiResponse.ok(service.applyOutline(draftId, request));
    }

    @PutMapping("/{nodeId}")
    public ApiResponse<DraftNodeDto> update(
            @PathVariable long draftId,
            @PathVariable long nodeId,
            @RequestBody(required = false) UpdateDraftNodeRequest request
    ) {
        return ApiResponse.ok(service.updateNode(draftId, nodeId, request));
    }

    @PutMapping("/{nodeId}/format-override")
    public ApiResponse<DraftNodeDto> updateFormatOverride(
            @PathVariable long draftId,
            @PathVariable long nodeId,
            @RequestBody(required = false) DraftNodeFormatOverride request
    ) {
        return ApiResponse.ok(service.saveFormatOverride(draftId, nodeId, request));
    }

    @DeleteMapping("/{nodeId}/format-override")
    public ApiResponse<DraftNodeDto> restoreTemplateDefaultFormatting(
            @PathVariable long draftId,
            @PathVariable long nodeId
    ) {
        return ApiResponse.ok(service.restoreTemplateDefaultFormatting(draftId, nodeId));
    }
}
