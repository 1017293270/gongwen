package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftNotFoundException;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeFormatOverride;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
import com.gongwen.assistant.material.MaterialDto;
import com.gongwen.assistant.material.MaterialRepository;
import com.gongwen.assistant.material.MaterialSaveCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiLocalOperationServiceTest {
    private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
    private final InMemoryMaterialRepository materialRepository = new InMemoryMaterialRepository();
    private final InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
    private final InMemoryDraftNodeRepository draftNodeRepository = new InMemoryDraftNodeRepository();

    @Test
    void generatesSuggestionWithoutSavingDraftBlocks() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "请各部门做好材料报送工作。", 30)
        ));
        materialRepository.materials = List.of(new MaterialPromptSummary(1L, "meeting.docx", "会议要求月底前完成"));
        AiLocalOperationService service = newService();

        AiLocalOperationResponse response = service.generateSuggestion(draft.id(), new AiLocalOperationRequest(
                2L,
                AiLocalOperationType.FORMALIZE,
                "突出时限要求"
        ));

        assertThat(response.traceId()).isNotNull();
        assertThat(response.targetBlockId()).isEqualTo(2L);
        assertThat(response.operationType()).isEqualTo(AiLocalOperationType.FORMALIZE);
        assertThat(response.suggestionText()).contains("FORMALIZE", "请各部门做好材料报送工作。");
        assertThat(draftRepository.replaceCalls).isZero();
        assertThat(traceRepository.saved.taskType()).isEqualTo("LOCAL_OPERATION");
        assertThat(traceRepository.saved.status()).isEqualTo("SUCCESS");
        assertThat(traceRepository.saved.promptVersion()).isEqualTo("local-operation-v1");
        assertThat(traceRepository.saved.inputSummary()).contains("targetBlockId=2", "operationType=FORMALIZE", "originalChars=");
        assertThat(traceRepository.saved.inputSummary()).doesNotContain("请各部门做好材料报送工作");
        assertThat(traceRepository.saved.outputSummary()).contains("suggestionChars=");
    }

    @Test
    void rejectsNonBodyTargetBlock() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "正文", 30)
        ));
        AiLocalOperationService service = newService();

        assertThatThrownBy(() -> service.generateSuggestion(draft.id(), new AiLocalOperationRequest(
                1L,
                AiLocalOperationType.REWRITE,
                ""
        )))
                .isInstanceOf(AiOutlineException.class)
                .hasMessageContaining("只能对正文段落");

        assertThat(draftRepository.replaceCalls).isZero();
    }

    @Test
    void targetsDraftNodeBeforeLegacyBlockAndDoesNotSaveSuggestion() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "旧块正文", 30)
        ));
        draftNodeRepository.nodes = List.of(draftNode(12L, draft.id(), "BODY", "正文", "节点正文", 30));
        AiLocalOperationService service = newService();

        AiLocalOperationResponse response = service.generateSuggestion(draft.id(), new AiLocalOperationRequest(
                null,
                12L,
                "BODY",
                "正文",
                "节点正文",
                AiLocalOperationType.REWRITE,
                "突出责任"
        ));

        assertThat(response.targetBlockId()).isNull();
        assertThat(response.targetNodeId()).isEqualTo(12L);
        assertThat(response.targetNodeRole()).isEqualTo("BODY");
        assertThat(response.suggestionText()).contains("REWRITE", "节点正文", "突出责任");
        assertThat(draftRepository.replaceCalls).isZero();
        assertThat(draftNodeRepository.updateCalls).isZero();
        assertThat(traceRepository.saved.inputSummary()).contains("targetNodeId=12", "targetNodeRole=BODY", "operationType=REWRITE");
        assertThat(traceRepository.saved.inputSummary()).doesNotContain("节点正文");
    }

    private AiLocalOperationService newService() {
        return new AiLocalOperationService(
                new DraftService(draftRepository),
                materialRepository,
                new PromptBuilder(),
                new LocalOperationModelAdapter(),
                traceRepository,
                draftNodeRepository
        );
    }

    private static final class LocalOperationModelAdapter implements ModelAdapter {
        @Override
        public String provider() {
            return "mock";
        }

        @Override
        public String modelName() {
            return "local-operation-model";
        }

        @Override
        public AiOutlineResponse generateOutline(OutlinePrompt prompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiLocalOperationModelResponse generateLocalOperation(LocalOperationPrompt prompt) {
            return new AiLocalOperationModelResponse(prompt.operationType() + "：" + prompt.originalText() + prompt.instruction());
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
        private int replaceCalls;

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
            replaceCalls++;
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

    private static DraftNode draftNode(long id, long draftId, String role, String title, String content, int sortOrder) {
        return new DraftNode(
                id,
                draftId,
                1L,
                "node-" + id,
                null,
                "PARAGRAPH",
                role,
                role.toLowerCase(),
                title,
                content,
                sortOrder,
                "USER_FILLED",
                DraftNodeFormatOverride.empty(),
                null,
                null
        );
    }

    private static final class InMemoryDraftNodeRepository implements DraftNodeRepository {
        private List<DraftNode> nodes = List.of();
        private int updateCalls;

        @Override
        public List<DraftNode> findByDraftId(long draftId) {
            return nodes.stream()
                    .filter(node -> node.draftId() == draftId)
                    .toList();
        }

        @Override
        public boolean existsByDraftId(long draftId) {
            return !findByDraftId(draftId).isEmpty();
        }

        @Override
        public List<DraftNode> replaceForDraft(long draftId, List<DraftNode> nodes) {
            this.nodes = nodes;
            return nodes;
        }

        @Override
        public Optional<DraftNode> updateContent(long draftId, long nodeId, String content, String status) {
            updateCalls++;
            return Optional.empty();
        }
    }
}
