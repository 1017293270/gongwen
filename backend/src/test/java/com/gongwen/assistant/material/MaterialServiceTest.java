package com.gongwen.assistant.material;

import com.gongwen.assistant.ai.MaterialPromptSummary;
import com.gongwen.assistant.draft.CreateDraftRequest;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftNotFoundException;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.draft.DraftService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialServiceTest {
    private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
    private final InMemoryMaterialRepository materialRepository = new InMemoryMaterialRepository();
    private final FakeMaterialStorage storage = new FakeMaterialStorage();
    private final FakeMaterialTextExtractor extractor = new FakeMaterialTextExtractor();
    private final MaterialService service = new MaterialService(
            new DraftService(draftRepository),
            materialRepository,
            storage,
            extractor,
            new MaterialProperties("storage/materials", 20 * 1024 * 1024L)
    );

    @Test
    void storesReadyMaterialWhenTextExtractionSucceeds() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "meeting.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "会议纪要".getBytes()
        );

        MaterialDto material = service.uploadMaterial(draft.id(), file);

        assertThat(material.draftId()).isEqualTo(draft.id());
        assertThat(material.originalFileName()).isEqualTo("meeting.docx");
        assertThat(material.fileExtension()).isEqualTo("docx");
        assertThat(material.status()).isEqualTo("READY");
        assertThat(material.extractedTextLength()).isEqualTo(4);
        assertThat(material.errorMessage()).isNull();
        assertThat(materialRepository.saved.extractedText()).isEqualTo("会议纪要");
        assertThat(storage.savedPath).contains("draft-1");
    }

    @Test
    void storesFailedMaterialWhenTextExtractionFails() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of());
        extractor.failure = new MaterialExtractionException("PDF 无法提取文本");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.pdf",
                "application/pdf",
                "not a real pdf".getBytes()
        );

        MaterialDto material = service.uploadMaterial(draft.id(), file);

        assertThat(material.status()).isEqualTo("FAILED");
        assertThat(material.extractedTextLength()).isZero();
        assertThat(material.errorMessage()).isEqualTo("PDF 无法提取文本");
        assertThat(materialRepository.saved.status()).isEqualTo("FAILED");
    }

    @Test
    void rejectsUnsupportedFileType() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "plain text".getBytes()
        );

        assertThatThrownBy(() -> service.uploadMaterial(draft.id(), file))
                .isInstanceOf(MaterialUploadException.class)
                .hasMessageContaining("仅支持上传 Word 或 PDF 材料");
    }

    private static final class FakeMaterialStorage implements MaterialStorage {
        private String savedPath;

        @Override
        public String save(long draftId, String originalFileName, String fileExtension, byte[] content) {
            this.savedPath = "storage/materials/draft-" + draftId + "/material." + fileExtension;
            return savedPath;
        }
    }

    private static final class FakeMaterialTextExtractor implements MaterialTextExtractor {
        private MaterialExtractionException failure;

        @Override
        public String extract(String fileExtension, byte[] content) {
            if (failure != null) {
                throw failure;
            }
            return new String(content);
        }
    }

    private static final class InMemoryMaterialRepository implements MaterialRepository {
        private MaterialSaveCommand saved;
        private long id = 1;

        @Override
        public MaterialDto save(MaterialSaveCommand command) {
            this.saved = command;
            return command.toDto(id++);
        }

        @Override
        public List<MaterialDto> findByDraftId(long draftId) {
            if (saved == null || saved.draftId() != draftId) {
                return List.of();
            }
            return List.of(saved.toDto(1));
        }

        @Override
        public List<MaterialPromptSummary> findReadyTextSummariesByDraftId(long draftId) {
            return List.of();
        }
    }

    private static final class InMemoryDraftRepository implements DraftRepository {
        private DraftDetailDto draft;
        private long id = 1;

        @Override
        public DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks) {
            draft = new DraftDetailDto(id++, documentTypeCode, title, "DRAFT", new ArrayList<>());
            return draft;
        }

        @Override
        public DraftDetailDto findById(long id) {
            if (draft == null || draft.id() != id) {
                throw new DraftNotFoundException(id);
            }
            return draft;
        }

        @Override
        public DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks) {
            return findById(id);
        }
    }
}
