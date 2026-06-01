package com.gongwen.assistant.rendering;

import com.gongwen.assistant.exporting.DraftWordExportService;
import com.gongwen.assistant.exporting.DraftWordRenderResult;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class DraftWordRenderPreviewSource implements DraftRenderPreviewSource {
    private final DraftWordExportService draftWordExportService;

    public DraftWordRenderPreviewSource(DraftWordExportService draftWordExportService) {
        this.draftWordExportService = draftWordExportService;
    }

    @Override
    public DraftRenderPreviewDocument renderDraft(long draftId) {
        DraftWordRenderResult result = draftWordExportService.renderDraftForPreview(draftId);
        return new DraftRenderPreviewDocument(
                result.templateVersionId(),
                sha256(result.content()),
                result.fileName(),
                result.content()
        );
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new RenderPreviewException("DRAFT_RENDER_PREVIEW_HASH_FAILED", "Draft render preview hash failed", exception);
        }
    }
}
