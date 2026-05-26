package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftNotFoundException;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.material.MaterialDto;
import com.gongwen.assistant.material.MaterialRepository;
import com.gongwen.assistant.material.MaterialSaveCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiOutlineServiceTest {
    private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
    private final InMemoryMaterialRepository materialRepository = new InMemoryMaterialRepository();
    private final InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();

    @Test
    void generatesOutlineAndRecordsSuccessTrace() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("RECIPIENT", "各部门", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "请按时报送材料。", 30)
        ));
        materialRepository.materials = List.of(new MaterialPromptSummary(1L, "meeting.docx", "会议材料"));
        AiOutlineService service = newService(new MockModelAdapter());

        AiOutlineResponse response = service.generateOutline(draft.id(), new AiOutlineRequest("突出时间要求"));

        assertThat(response.traceId()).isNotNull();
        assertThat(response.titleSuggestion()).isEqualTo("测试通知");
        assertThat(response.sections()).hasSize(3);
        assertThat(traceRepository.saved.status()).isEqualTo("SUCCESS");
        assertThat(traceRepository.saved.inputSummary()).contains("draftBlocks=3");
        assertThat(traceRepository.saved.inputSummary()).doesNotContain("会议材料");
        assertThat(traceRepository.saved.outputSummary()).contains("sections=3");
    }

    @Test
    void recordsFailedTraceWhenAdapterFails() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10)
        ));
        AiOutlineService service = newService(new FailingModelAdapter());

        assertThatThrownBy(() -> service.generateOutline(draft.id(), new AiOutlineRequest("")))
                .isInstanceOf(AiOutlineException.class)
                .hasMessageContaining("AI 服务暂不可用");

        assertThat(traceRepository.saved.status()).isEqualTo("FAILED");
        assertThat(traceRepository.saved.errorCode()).isEqualTo("MODEL_TIMEOUT");
        assertThat(traceRepository.saved.errorMessage()).isEqualTo("模型请求超时");
    }

    @Test
    void rejectsOverlongInstructionBeforeCallingAdapter() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of());
        AiOutlineService service = newService(new MockModelAdapter());

        assertThatThrownBy(() -> service.generateOutline(draft.id(), new AiOutlineRequest("长".repeat(1001))))
                .isInstanceOf(AiOutlineException.class)
                .hasMessageContaining("补充要求不能超过 1000 字");

        assertThat(traceRepository.saved).isNull();
    }

    private AiOutlineService newService(ModelAdapter adapter) {
        return new AiOutlineService(
                new DraftService(draftRepository),
                materialRepository,
                new PromptBuilder(),
                adapter,
                traceRepository
        );
    }

    private static final class FailingModelAdapter implements ModelAdapter {
        @Override
        public String provider() {
            return "mock";
        }

        @Override
        public String modelName() {
            return "failing-model";
        }

        @Override
        public AiOutlineResponse generateOutline(OutlinePrompt prompt) {
            throw new ModelAdapterException("MODEL_TIMEOUT", "模型请求超时");
        }
    }

    private static final class InMemoryTraceRepository implements AiGenerationTraceRepository {
        private AiGenerationTrace saved;

        @Override
        public void save(AiGenerationTrace trace) {
            this.saved = trace;
        }
    }

    private static final class InMemoryMaterialRepository implements MaterialRepository {
        private List<MaterialPromptSummary> materials = List.of();

        @Override
        public MaterialDto save(MaterialSaveCommand command) {
            return new MaterialDto(
                    1L,
                    command.draftId(),
                    command.originalFileName(),
                    command.contentType(),
                    command.fileSizeBytes(),
                    command.fileExtension(),
                    command.status(),
                    command.extractedText() == null ? 0 : command.extractedText().length(),
                    command.errorMessage()
            );
        }

        @Override
        public List<MaterialDto> findByDraftId(long draftId) {
            return List.of();
        }

        @Override
        public List<MaterialPromptSummary> findReadyTextSummariesByDraftId(long draftId) {
            return materials;
        }
    }

    private static final class InMemoryDraftRepository implements DraftRepository {
        private DraftDetailDto draft;
        private long id = 1;

        @Override
        public DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks) {
            List<DraftBlockDto> blockDtos = new ArrayList<>();
            long blockId = 1;
            for (DraftBlockUpdateRequest block : blocks) {
                blockDtos.add(new DraftBlockDto(blockId++, block.blockType(), block.content(), block.sortOrder()));
            }
            draft = new DraftDetailDto(id++, documentTypeCode, title, "DRAFT", blockDtos);
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
