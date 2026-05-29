package com.gongwen.assistant.quality;

import com.gongwen.assistant.ai.AiGenerationTrace;
import com.gongwen.assistant.ai.AiGenerationTraceRepository;
import com.gongwen.assistant.ai.AiOutlineResponse;
import com.gongwen.assistant.ai.AiQualityReviewResponse;
import com.gongwen.assistant.ai.AiQualitySuggestion;
import com.gongwen.assistant.ai.MaterialPromptSummary;
import com.gongwen.assistant.ai.ModelAdapter;
import com.gongwen.assistant.ai.PromptBuilder;
import com.gongwen.assistant.ai.QualityCheckPrompt;
import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftNotFoundException;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.material.MaterialDto;
import com.gongwen.assistant.material.MaterialRepository;
import com.gongwen.assistant.material.MaterialSaveCommand;
import com.gongwen.assistant.template.profile.TemplateProfile;
import com.gongwen.assistant.template.profile.TemplateProfileRepository;
import com.gongwen.assistant.template.profile.TemplateEffectiveFormattingService;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingRepository;
import com.gongwen.assistant.template.profile.TemplateStructureFormattingProfile;
import com.gongwen.assistant.template.profile.TemplateStructureProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QualityCheckServiceTest {
    private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
    private final InMemoryMaterialRepository materialRepository = new InMemoryMaterialRepository();
    private final InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
    private final InMemoryQualityCheckRepository qualityCheckRepository = new InMemoryQualityCheckRepository();
    private final EmptyTemplateProfileRepository emptyTemplateProfileRepository = new EmptyTemplateProfileRepository();
    private final EmptyTemplateStructureFormattingRepository emptyTemplateStructureFormattingRepository =
            new EmptyTemplateStructureFormattingRepository();

    @Test
    void combinesRuleErrorsWithAiSuggestionsAndPersistsResult() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("RECIPIENT", "", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "请各部门按时报送材料。", 30),
                new DraftBlockUpdateRequest("SIGNATURE", "办公室", 90),
                new DraftBlockUpdateRequest("DATE", "2026年5月26日", 100)
        ));
        materialRepository.materials = List.of(new MaterialPromptSummary(1L, "meeting.docx", "会议要求月底前完成"));
        QualityCheckService service = newService();

        QualityCheckResponse response = service.runCheck(draft.id());

        assertThat(response.status()).isEqualTo("ERROR");
        assertThat(response.exportBlocked()).isTrue();
        assertThat(response.items()).extracting(QualityCheckItem::code)
                .contains("REQUIRED_RECIPIENT_MISSING", "AI_EXPRESSION_CLARITY");
        assertThat(traceRepository.saved.taskType()).isEqualTo("QUALITY_CHECK");
        assertThat(traceRepository.saved.promptVersion()).isEqualTo("quality-check-v1");
        assertThat(traceRepository.saved.inputSummary()).contains("draftBlocks=5", "materials=1");
        assertThat(qualityCheckRepository.latest).isEqualTo(response);
    }

    @Test
    void marksAiFailureAsWarningWithoutBlockingExportWhenRulesPass() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "测试通知", List.of(
                new DraftBlockUpdateRequest("TITLE", "测试通知", 10),
                new DraftBlockUpdateRequest("RECIPIENT", "各部门", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "请各部门按时报送材料。", 30),
                new DraftBlockUpdateRequest("SIGNATURE", "办公室", 90),
                new DraftBlockUpdateRequest("DATE", "2026年5月26日", 100)
        ));
        QualityCheckService service = new QualityCheckService(
                new DraftService(draftRepository),
                materialRepository,
                new PromptBuilder(),
                new FailingQualityModelAdapter(),
                traceRepository,
                qualityCheckRepository,
                emptyTemplateProfileRepository,
                emptyTemplateStructureFormattingRepository,
                new TemplateEffectiveFormattingService()
        );

        QualityCheckResponse response = service.runCheck(draft.id());

        assertThat(response.status()).isEqualTo("WARNING");
        assertThat(response.exportBlocked()).isFalse();
        assertThat(response.items()).extracting(QualityCheckItem::code)
                .contains("AI_QUALITY_UNAVAILABLE");
        assertThat(traceRepository.saved.status()).isEqualTo("FAILED");
    }

    @Test
    void addsFormattingWarningsFromEffectiveTemplateFormatting() {
        long templateVersionId = 41L;
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "格式风险", List.of(
                new DraftBlockUpdateRequest("TITLE", "格式风险", 10),
                new DraftBlockUpdateRequest("RECIPIENT", "各部门", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "第一段正文", 30),
                new DraftBlockUpdateRequest("SIGNATURE", "办公室", 90),
                new DraftBlockUpdateRequest("DATE", "2026年5月29日", 100)
        ));
        draftRepository.updateTemplateVersion(draft.id(), templateVersionId);
        QualityCheckService service = newServiceWithTemplateFormatting(
                formattingProfile(
                        structure("title-1", "TITLE", "LEFT", 0, 0),
                        structure("body-1", "BODY", "LEFT", 0, null),
                        structure("signature-1", "SIGNATURE", "CENTER", 0, 0),
                        structure("date-1", "DATE", "LEFT", 0, 0)
                ),
                Map.of()
        );

        QualityCheckResponse response = service.runCheck(draft.id());

        assertThat(response.exportBlocked()).isFalse();
        assertThat(response.items()).extracting(QualityCheckItem::code)
                .contains(
                        "TEMPLATE_TITLE_ALIGNMENT_RISK",
                        "TEMPLATE_BODY_INDENT_RISK",
                        "TEMPLATE_BODY_SPACING_RISK",
                        "TEMPLATE_SIGNATURE_ALIGNMENT_RISK",
                        "TEMPLATE_DATE_ALIGNMENT_RISK"
                );
        assertThat(response.items().stream()
                .filter(item -> item.code().startsWith("TEMPLATE_") && item.code().endsWith("_RISK"))
                .map(QualityCheckItem::severity))
                .containsOnly("WARNING");
    }

    @Test
    void usesFormattingOverridesBeforeRaisingTemplateWarnings() {
        long templateVersionId = 42L;
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "格式覆盖", List.of(
                new DraftBlockUpdateRequest("TITLE", "格式覆盖", 10),
                new DraftBlockUpdateRequest("RECIPIENT", "各部门", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "第一段正文", 30),
                new DraftBlockUpdateRequest("SIGNATURE", "办公室", 90),
                new DraftBlockUpdateRequest("DATE", "2026年5月29日", 100)
        ));
        draftRepository.updateTemplateVersion(draft.id(), templateVersionId);
        QualityCheckService service = newServiceWithTemplateFormatting(
                formattingProfile(
                        structure("title-1", "TITLE", "LEFT", 0, 0),
                        structure("body-1", "BODY", "LEFT", 0, null),
                        structure("signature-1", "SIGNATURE", "LEFT", 0, 0),
                        structure("date-1", "DATE", "LEFT", 0, 0)
                ),
                Map.of(
                        "title-1", new TemplateStructureFormattingProfile(null, null, null, "CENTER", null, null, null, null),
                        "body-1", new TemplateStructureFormattingProfile(null, null, null, null, 560, 360, null, null),
                        "signature-1", new TemplateStructureFormattingProfile(null, null, null, "RIGHT", null, null, null, null),
                        "date-1", new TemplateStructureFormattingProfile(null, null, null, "RIGHT", null, null, null, null)
                )
        );

        QualityCheckResponse response = service.runCheck(draft.id());

        assertThat(response.items()).extracting(QualityCheckItem::code)
                .doesNotContain(
                        "TEMPLATE_TITLE_ALIGNMENT_RISK",
                        "TEMPLATE_BODY_INDENT_RISK",
                        "TEMPLATE_BODY_SPACING_RISK",
                        "TEMPLATE_SIGNATURE_ALIGNMENT_RISK",
                        "TEMPLATE_DATE_ALIGNMENT_RISK"
                );
    }

    @Test
    void degradesNullAiResponseToInvalidWarningWithoutBlockingWhenRulesPass() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "异常响应", List.of(
                new DraftBlockUpdateRequest("TITLE", "异常响应", 10),
                new DraftBlockUpdateRequest("RECIPIENT", "各部门", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "第一段正文", 30),
                new DraftBlockUpdateRequest("SIGNATURE", "办公室", 90),
                new DraftBlockUpdateRequest("DATE", "2026年5月29日", 100)
        ));
        QualityCheckService service = new QualityCheckService(
                new DraftService(draftRepository),
                materialRepository,
                new PromptBuilder(),
                new NullResponseQualityModelAdapter(),
                traceRepository,
                qualityCheckRepository,
                emptyTemplateProfileRepository,
                emptyTemplateStructureFormattingRepository,
                new TemplateEffectiveFormattingService()
        );

        QualityCheckResponse response = service.runCheck(draft.id());

        assertThat(response.status()).isEqualTo("WARNING");
        assertThat(response.exportBlocked()).isFalse();
        assertThat(response.items()).extracting(QualityCheckItem::code)
                .contains("AI_QUALITY_RESPONSE_INVALID");
        assertThat(traceRepository.saved.status()).isEqualTo("FAILED");
    }

    @Test
    void addsFormattingWarningWhenEffectiveFormattingSlotIsMissing() {
        long templateVersionId = 43L;
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "缺少格式槽位", List.of(
                new DraftBlockUpdateRequest("TITLE", "缺少格式槽位", 10),
                new DraftBlockUpdateRequest("RECIPIENT", "各部门", 20),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "第一段正文", 30),
                new DraftBlockUpdateRequest("SIGNATURE", "办公室", 90),
                new DraftBlockUpdateRequest("DATE", "2026年5月29日", 100)
        ));
        draftRepository.updateTemplateVersion(draft.id(), templateVersionId);
        QualityCheckService service = newServiceWithTemplateFormatting(
                formattingProfile(
                        structure("title-1", "TITLE", "CENTER", 0, 0),
                        structure("body-1", "BODY", "LEFT", 560, 360),
                        structure("signature-1", "SIGNATURE", "RIGHT", 0, 0)
                ),
                Map.of()
        );

        QualityCheckResponse response = service.runCheck(draft.id());

        assertThat(response.items()).extracting(QualityCheckItem::code)
                .contains("TEMPLATE_DATE_ALIGNMENT_RISK");
    }

    private QualityCheckService newService() {
        return new QualityCheckService(
                new DraftService(draftRepository),
                materialRepository,
                new PromptBuilder(),
                new QualityModelAdapter(),
                traceRepository,
                qualityCheckRepository,
                emptyTemplateProfileRepository,
                emptyTemplateStructureFormattingRepository,
                new TemplateEffectiveFormattingService()
        );
    }

    private QualityCheckService newServiceWithTemplateFormatting(
            TemplateProfile profile,
            Map<String, TemplateStructureFormattingProfile> overrides
    ) {
        return new QualityCheckService(
                new DraftService(draftRepository),
                materialRepository,
                new PromptBuilder(),
                new QualityModelAdapter(),
                traceRepository,
                qualityCheckRepository,
                new FixedTemplateProfileRepository(profile),
                new FixedTemplateStructureFormattingRepository(overrides),
                new TemplateEffectiveFormattingService()
        );
    }

    private TemplateProfile formattingProfile(TemplateStructureProfile... structures) {
        return new TemplateProfile(
                1,
                List.of(structures),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private TemplateStructureProfile structure(
            String key,
            String type,
            String alignment,
            Integer indentationFirstLine,
            Integer spacingBetween
    ) {
        return new TemplateStructureProfile(
                key,
                type,
                type,
                type + " preview",
                "PARAGRAPH",
                null,
                null,
                "PROFILE",
                new TemplateStructureFormattingProfile(
                        "FangSong",
                        32,
                        false,
                        alignment,
                        indentationFirstLine,
                        spacingBetween,
                        0,
                        0
                )
        );
    }

    private static class QualityModelAdapter implements ModelAdapter {
        @Override
        public String provider() {
            return "deepseek";
        }

        @Override
        public String modelName() {
            return "quality-model";
        }

        @Override
        public AiOutlineResponse generateOutline(com.gongwen.assistant.ai.OutlinePrompt prompt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiQualityReviewResponse generateQualityReview(QualityCheckPrompt prompt) {
            return new AiQualityReviewResponse(List.of(new AiQualitySuggestion(
                    "WARNING",
                    "AI_EXPRESSION",
                    "AI_EXPRESSION_CLARITY",
                    "责任要求还可以更明确。",
                    "建议补充责任部门和完成时限。"
            )));
        }
    }

    private static final class FailingQualityModelAdapter extends QualityModelAdapter {
        @Override
        public AiQualityReviewResponse generateQualityReview(QualityCheckPrompt prompt) {
            throw new com.gongwen.assistant.ai.ModelAdapterException("AI_DEEPSEEK_HTTP_ERROR", "failed");
        }
    }

    private static final class NullResponseQualityModelAdapter extends QualityModelAdapter {
        @Override
        public AiQualityReviewResponse generateQualityReview(QualityCheckPrompt prompt) {
            return null;
        }
    }

    private static final class InMemoryTraceRepository implements AiGenerationTraceRepository {
        private AiGenerationTrace saved;

        @Override
        public void save(AiGenerationTrace trace) {
            this.saved = trace;
        }
    }

    private static final class InMemoryQualityCheckRepository implements QualityCheckRepository {
        private QualityCheckResponse latest;

        @Override
        public void save(QualityCheckRecord record) {
            latest = record.result();
        }

        @Override
        public Optional<QualityCheckResponse> findLatestByDraftId(long draftId) {
            return Optional.ofNullable(latest);
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
            draft = new DraftDetailDto(
                    id,
                    existing.documentTypeCode(),
                    existing.title(),
                    existing.status(),
                    existing.templateVersionId(),
                    toDtos(blocks)
            );
            return draft;
        }

        @Override
        public DraftDetailDto updateTemplateVersion(long id, Long templateVersionId) {
            DraftDetailDto existing = findById(id);
            draft = new DraftDetailDto(
                    id,
                    existing.documentTypeCode(),
                    existing.title(),
                    existing.status(),
                    templateVersionId,
                    existing.blocks()
            );
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

    private record FixedTemplateProfileRepository(TemplateProfile profile) implements TemplateProfileRepository {
        @Override
        public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.ofNullable(profile);
        }
    }

    private record FixedTemplateStructureFormattingRepository(
            Map<String, TemplateStructureFormattingProfile> overrides
    ) implements TemplateStructureFormattingRepository {
        @Override
        public Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId) {
            return overrides;
        }

        @Override
        public void saveOverride(
                long templateVersionId,
                String structureKey,
                TemplateStructureFormattingProfile formatting
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EmptyTemplateProfileRepository implements TemplateProfileRepository {
        @Override
        public void save(long templateVersionId, TemplateProfile profile, String profileHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<TemplateProfile> findByTemplateVersionId(long templateVersionId) {
            return Optional.empty();
        }
    }

    private static final class EmptyTemplateStructureFormattingRepository implements TemplateStructureFormattingRepository {
        @Override
        public Map<String, TemplateStructureFormattingProfile> findOverrides(long templateVersionId) {
            return Map.of();
        }

        @Override
        public void saveOverride(
                long templateVersionId,
                String structureKey,
                TemplateStructureFormattingProfile formatting
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
