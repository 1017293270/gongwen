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
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiParagraphServiceTest {
    private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
    private final InMemoryMaterialRepository materialRepository = new InMemoryMaterialRepository();
    private final InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();

    @Test
    void generatesParagraphAndSavesItAsDraftBlock() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("RECIPIENT", "各部门", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30),
                new DraftBlockUpdateRequest("SIGNATURE", "办公室", 50)
        ));
        materialRepository.materials = List.of(new MaterialPromptSummary(1L, "meeting.docx", "会议强调按时报送"));
        AiParagraphService service = newService();

        AiParagraphResponse response = service.generateParagraph(draft.id(), new AiParagraphRequest(
                "一、主要事项",
                List.of("明确工作安排", "说明时间要求"),
                "语气正式",
                30
        ));

        assertThat(response.traceId()).isNotNull();
        assertThat(response.block().blockType()).isEqualTo("BODY_PARAGRAPH");
        assertThat(response.block().sortOrder()).isEqualTo(30);
        assertThat(response.block().content()).contains("一、主要事项", "明确工作安排");
        assertThat(response.draft().blocks()).extracting(DraftBlockDto::content)
                .contains("一、主要事项：明确工作安排；说明时间要求。语气正式");
        assertThat(traceRepository.saved.taskType()).isEqualTo("PARAGRAPH");
        assertThat(traceRepository.saved.status()).isEqualTo("SUCCESS");
        assertThat(traceRepository.saved.inputSummary()).contains("headingChars=6");
        assertThat(traceRepository.saved.inputSummary()).doesNotContain("会议强调按时报送");
    }

    @Test
    void replacesExistingGeneratedParagraphAtSameSortOrder() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "旧正文", 30)
        ));
        AiParagraphService service = newService();

        DraftDetailDto updated = service.generateParagraph(draft.id(), new AiParagraphRequest(
                "一、主要事项",
                List.of("新的安排"),
                "",
                30
        )).draft();

        assertThat(updated.blocks()).filteredOn(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .hasSize(1)
                .first()
                .extracting(DraftBlockDto::content)
                .isEqualTo("一、主要事项：新的安排。");
    }

    private AiParagraphService newService() {
        return new AiParagraphService(
                new DraftService(draftRepository),
                materialRepository,
                new PromptBuilder(),
                new ParagraphModelAdapter(),
                traceRepository
        );
    }

    private static final class ParagraphModelAdapter implements ModelAdapter {
        @Override
        public String provider() {
            return "mock";
        }

        @Override
        public String modelName() {
            return "paragraph-model";
        }

        @Override
        public AiOutlineResponse generateOutline(OutlinePrompt prompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiParagraphModelResponse generateParagraph(ParagraphPrompt prompt) {
            String points = String.join("；", prompt.points());
            String suffix = prompt.instruction().isBlank() ? "" : prompt.instruction();
            return new AiParagraphModelResponse(prompt.heading() + "：" + points + "。" + suffix);
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
            return null;
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
            draft = new DraftDetailDto(id++, documentTypeCode, title, "DRAFT", toDtos(blocks));
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
            DraftDetailDto existing = findById(id);
            draft = new DraftDetailDto(id, existing.documentTypeCode(), existing.title(), existing.status(), toDtos(blocks));
            return draft;
        }

        private List<DraftBlockDto> toDtos(List<DraftBlockUpdateRequest> blocks) {
            List<DraftBlockDto> sorted = new ArrayList<>();
            List<DraftBlockUpdateRequest> ordered = blocks.stream()
                    .sorted(Comparator.comparing(DraftBlockUpdateRequest::sortOrder))
                    .toList();
            long blockId = 1;
            for (DraftBlockUpdateRequest block : ordered) {
                sorted.add(new DraftBlockDto(blockId++, block.blockType(), block.content(), block.sortOrder()));
            }
            return sorted;
        }
    }
}
