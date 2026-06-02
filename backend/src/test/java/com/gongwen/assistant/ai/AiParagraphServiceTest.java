package com.gongwen.assistant.ai;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftNotFoundException;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeFormatOverride;
import com.gongwen.assistant.draft.node.DraftNodeFormattingResolver;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
import com.gongwen.assistant.material.MaterialDto;
import com.gongwen.assistant.material.MaterialRepository;
import com.gongwen.assistant.material.MaterialSaveCommand;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateLineSpacingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiParagraphServiceTest {
    private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
    private final InMemoryMaterialRepository materialRepository = new InMemoryMaterialRepository();
    private final InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
    private final InMemoryDraftNodeRepository draftNodeRepository = new InMemoryDraftNodeRepository();

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

    @Test
    void prefixesOutlineHeadingWhenModelOmitsIt() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30)
        ));
        AiParagraphService service = newService(new FixedParagraphModelAdapter("说明安排。"));

        DraftDetailDto updated = service.generateParagraph(draft.id(), new AiParagraphRequest(
                "二、工作安排",
                List.of("说明安排"),
                "",
                30
        )).draft();

        assertThat(updated.blocks()).filteredOn(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .first()
                .extracting(DraftBlockDto::content)
                .isEqualTo("二、工作安排：说明安排。");
    }

    @Test
    void writesGeneratedParagraphToTargetDraftNodeWithoutChangingRole() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30)
        ));
        draftNodeRepository.nodes = List.of(draftNode(10L, draft.id(), "BODY", "正文", "", 30));
        AiParagraphService service = newService();

        AiParagraphResponse response = service.generateParagraph(draft.id(), new AiParagraphRequest(
                "一、主要事项",
                List.of("节点化生成"),
                "",
                30,
                10L,
                "BODY",
                "正文",
                "旧节点内容"
        ));

        assertThat(response.node()).isNotNull();
        assertThat(response.node().id()).isEqualTo(10L);
        assertThat(response.node().role()).isEqualTo("BODY");
        assertThat(response.node().status()).isEqualTo("AI_GENERATED");
        assertThat(response.node().baseFormatting()).isEqualTo(sourceBodyFormatting());
        assertThat(response.node().effectiveFormatting()).isEqualTo(sourceBodyFormatting());
        assertThat(response.node().content()).contains("一、主要事项", "节点化生成");
        assertThat(draftNodeRepository.updatedRole).isEqualTo("BODY");
        assertThat(traceRepository.saved.inputSummary()).contains("nodeId=10", "nodeRole=BODY", "nodeContextChars=");
        assertThat(traceRepository.saved.inputSummary()).doesNotContain("旧节点内容");
        assertThat(traceRepository.saved.outputSummary()).contains("nodeId=10", "nodeRole=BODY");
    }

    @Test
    void rejectsProtectedTargetNodeForParagraphGeneration() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30)
        ));
        draftNodeRepository.nodes = List.of(draftNode(11L, draft.id(), "TITLE", "标题", "测试通知", 10));
        AiParagraphService service = newService();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateParagraph(draft.id(), new AiParagraphRequest(
                "一、主要事项",
                List.of("不得改标题"),
                "",
                30,
                11L,
                "TITLE",
                "标题",
                "测试通知"
        )))
                .isInstanceOf(AiOutlineException.class)
                .hasMessageContaining("段落生成不能直接改写标题");

        assertThat(draftNodeRepository.updateCalls).isZero();
    }

    private AiParagraphService newService() {
        return newService(new ParagraphModelAdapter());
    }

    private AiParagraphService newService(ModelAdapter modelAdapter) {
        return new AiParagraphService(
                new DraftService(draftRepository),
                materialRepository,
                new PromptBuilder(),
                modelAdapter,
                traceRepository,
                draftNodeRepository,
                formattingResolver()
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

    private static final class FixedParagraphModelAdapter implements ModelAdapter {
        private final String content;

        private FixedParagraphModelAdapter(String content) {
            this.content = content;
        }

        @Override
        public String provider() {
            return "mock";
        }

        @Override
        public String modelName() {
            return "fixed-paragraph-model";
        }

        @Override
        public AiOutlineResponse generateOutline(OutlinePrompt prompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiParagraphModelResponse generateParagraph(ParagraphPrompt prompt) {
            return new AiParagraphModelResponse(content);
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
            draft = new DraftDetailDto(id++, documentTypeCode, title, "DRAFT", 9L, toDtos(blocks));
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

    private static DraftNodeFormattingResolver formattingResolver() {
        return new DraftNodeFormattingResolver(
                new FixedDocumentStructureProfileRepository(),
                new FixedTemplateStructureFormattingRepository(),
                new TemplateEffectiveFormattingService()
        );
    }

    private static TemplateStructureFormattingProfile sourceBodyFormatting() {
        return new TemplateStructureFormattingProfile(
                "SourceFangSong",
                32,
                false,
                "BOTH",
                720,
                180,
                0,
                0,
                null,
                "SourceFangSong",
                "Times New Roman",
                new TemplateLineSpacingProfile("AUTO", null, 180)
        );
    }

    private record FixedDocumentStructureProfileRepository() implements DocumentStructureProfileRepository {
        @Override
        public void save(long templateVersionId, DocumentStructureProfile profile) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DocumentStructureProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.of(new DocumentStructureProfile(
                    1,
                    "hash",
                    "document-structure-v1",
                    List.of(new DocumentNode(
                            "node-10",
                            null,
                            "PARAGRAPH",
                            "BODY",
                            "Source body",
                            "Source body",
                            10,
                            "/node-10",
                            sourceBodyFormatting(),
                            List.of()
                    )),
                    List.of(),
                    List.of(),
                    List.of(),
                    Instant.now()
            ));
        }
    }

    private record FixedTemplateStructureFormattingRepository() implements TemplateStructureFormattingRepository {
        @Override
        public Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId) {
            return Map.of();
        }

        @Override
        public void saveOverride(long templateVersionId, String structureKey, TemplateStructureFormattingProfile formatting) {
            throw new UnsupportedOperationException();
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
                content == null || content.isBlank() ? "EMPTY" : "USER_FILLED",
                DraftNodeFormatOverride.empty(),
                null,
                null
        );
    }

    private static final class InMemoryDraftNodeRepository implements DraftNodeRepository {
        private List<DraftNode> nodes = List.of();
        private int updateCalls;
        private String updatedRole;

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
            Optional<DraftNode> existing = findByDraftId(draftId).stream()
                    .filter(node -> node.id() == nodeId)
                    .findFirst();
            existing.ifPresent(node -> updatedRole = node.role());
            DraftNode updated = existing
                    .map(node -> new DraftNode(
                            node.id(),
                            node.draftId(),
                            node.structureMappingProfileId(),
                            node.templateNodeKey(),
                            node.parentTemplateNodeKey(),
                            node.nodeType(),
                            node.role(),
                            node.slotKey(),
                            node.title(),
                            content,
                            node.sortOrder(),
                            status,
                            node.formatOverride(),
                            node.createdAt(),
                            node.updatedAt()
                    ))
                    .orElse(null);
            if (updated == null) {
                return Optional.empty();
            }
            nodes = nodes.stream()
                    .map(node -> node.id() == nodeId ? updated : node)
                    .toList();
            return Optional.of(updated);
        }
    }
}
