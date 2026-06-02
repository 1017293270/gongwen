package com.gongwen.assistant.ai.candidate;

import com.gongwen.assistant.draft.DraftBlockDto;
import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftNotFoundException;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.draft.DraftService;
import com.gongwen.assistant.draft.UpdateDraftBlocksRequest;
import com.gongwen.assistant.documentstructure.DocumentNode;
import com.gongwen.assistant.documentstructure.DocumentStructureProfile;
import com.gongwen.assistant.documentstructure.DocumentStructureProfileRepository;
import com.gongwen.assistant.draft.node.DraftNode;
import com.gongwen.assistant.draft.node.DraftNodeFormatOverride;
import com.gongwen.assistant.draft.node.DraftNodeFormattingResolver;
import com.gongwen.assistant.draft.node.DraftNodeMetadata;
import com.gongwen.assistant.draft.node.DraftNodeRepository;
import com.gongwen.assistant.security.CurrentUser;
import com.gongwen.assistant.security.CurrentUserProvider;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiParagraphCandidateServiceTest {
    private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
    private final InMemoryDraftNodeRepository draftNodeRepository = new InMemoryDraftNodeRepository();
    private final InMemoryCandidateRepository candidateRepository = new InMemoryCandidateRepository();
    private final AiParagraphCandidateService service = new AiParagraphCandidateService(
            new DraftService(draftRepository),
            candidateRepository,
            draftNodeRepository,
            formattingResolver(),
            new FixedCurrentUserProvider()
    );

    @Test
    void createBatchDoesNotChangeDraftNodesOrBlocks() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "Draft", List.of(
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "existing body", 30)
        ));
        draftNodeRepository.nodes = List.of(draftNode(10L, draft.id(), "BODY", "Body", "existing node", 30, "USER_FILLED"));

        List<AiParagraphCandidateDto> candidates = service.createBatch(draft.id(), new CreateParagraphCandidateBatchRequest(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "outline generated",
                List.of(new ParagraphCandidateSectionRequest(
                        10L,
                        "BODY",
                        "Body",
                        1,
                        "Section one",
                        List.of("point a", "point b"),
                        "candidate text"
                ))
        ));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().status()).isEqualTo("PENDING");
        assertThat(candidates.getFirst().candidateText()).isEqualTo("candidate text");
        assertThat(draftRepository.draft.blocks()).extracting(DraftBlockDto::content).containsExactly("existing body");
        assertThat(draftNodeRepository.findByDraftId(draft.id())).extracting(DraftNode::content).containsExactly("existing node");
        assertThat(draftRepository.replaceCalls).isZero();
        assertThat(draftNodeRepository.updateCalls).isZero();
    }

    @Test
    void acceptWritesTargetNodeAndCompatibleDraftBlock() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "Draft", List.of(
                new DraftBlockUpdateRequest("TITLE", "Draft", 10),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30)
        ));
        draftNodeRepository.nodes = List.of(draftNode(10L, draft.id(), "BODY", "Body", "", 30, "EMPTY"));
        AiParagraphCandidate ready = candidateRepository.insert(candidate(draft.id(), 10L, 1, "READY", "accepted body"));

        AiParagraphCandidateAcceptResponse response = service.accept(draft.id(), ready.id());

        assertThat(response.candidate().status()).isEqualTo("ACCEPTED");
        assertThat(response.node()).isNotNull();
        assertThat(response.node().content()).isEqualTo("accepted body");
        assertThat(response.node().status()).isEqualTo("AI_GENERATED");
        assertThat(response.node().baseFormatting()).isEqualTo(sourceBodyFormatting());
        assertThat(response.node().effectiveFormatting()).isEqualTo(sourceBodyFormatting());
        assertThat(response.draft().blocks())
                .filteredOn(block -> "BODY_PARAGRAPH".equals(block.blockType()))
                .singleElement()
                .extracting(DraftBlockDto::content)
                .isEqualTo("accepted body");
        assertThat(candidateRepository.findById(ready.id())).map(AiParagraphCandidate::acceptedBy).contains(5L);
    }

    @Test
    void acceptEditedCandidateMarksNodeAsUserModifiedAfterAi() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "Draft", List.of(
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30)
        ));
        draftNodeRepository.nodes = List.of(draftNode(10L, draft.id(), "BODY", "Body", "", 30, "EMPTY"));
        AiParagraphCandidate edited = candidateRepository.insert(candidate(draft.id(), 10L, 1, "EDITED", "edited body"));

        AiParagraphCandidateAcceptResponse response = service.accept(draft.id(), edited.id());

        assertThat(response.node().status()).isEqualTo("USER_MODIFIED_AFTER_AI");
        assertThat(response.node().content()).isEqualTo("edited body");
    }

    @Test
    void batchAcceptSkipsNonReadyCandidatesAndAcceptsReadyOrEdited() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "Draft", List.of(
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30),
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 40)
        ));
        draftNodeRepository.nodes = List.of(
                draftNode(10L, draft.id(), "BODY", "Body one", "", 30, "EMPTY"),
                draftNode(11L, draft.id(), "BODY", "Body two", "", 40, "EMPTY"),
                draftNode(12L, draft.id(), "BODY", "Body failed", "", 50, "EMPTY"),
                draftNode(13L, draft.id(), "BODY", "Body discarded", "", 60, "EMPTY")
        );
        AiParagraphCandidate ready = candidateRepository.insert(candidate(draft.id(), 10L, 1, "READY", "ready body"));
        AiParagraphCandidate edited = candidateRepository.insert(candidate(draft.id(), 11L, 2, "EDITED", "edited body"));
        AiParagraphCandidate error = candidateRepository.insert(candidate(draft.id(), 12L, 3, "ERROR", "error body"));
        AiParagraphCandidate discarded = candidateRepository.insert(candidate(draft.id(), 13L, 4, "DISCARDED", "discarded body"));

        AiParagraphCandidateBatchAcceptResponse response = service.acceptBatch(draft.id(), new AcceptParagraphCandidateBatchRequest(null));

        assertThat(response.acceptedIds()).containsExactly(ready.id(), edited.id());
        assertThat(response.skippedIds()).containsExactly(error.id(), discarded.id());
        assertThat(candidateRepository.findById(ready.id())).map(AiParagraphCandidate::status).contains("ACCEPTED");
        assertThat(candidateRepository.findById(edited.id())).map(AiParagraphCandidate::status).contains("ACCEPTED");
        assertThat(candidateRepository.findById(error.id())).map(AiParagraphCandidate::status).contains("ERROR");
        assertThat(response.updatedDraft().blocks()).extracting(DraftBlockDto::content)
                .contains("ready body", "edited body");
    }

    @Test
    void candidateForAnotherDraftCannotBeAcceptedThroughThisDraft() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "Draft", List.of(
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30)
        ));
        DraftDetailDto otherDraft = draftRepository.createDraft("NOTICE", "Other draft", List.of(
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30)
        ));
        draftNodeRepository.nodes = List.of(
                draftNode(10L, draft.id(), "BODY", "Body", "", 30, "EMPTY"),
                draftNode(20L, otherDraft.id(), "BODY", "Other body", "", 30, "EMPTY")
        );
        AiParagraphCandidate otherCandidate = candidateRepository.insert(candidate(otherDraft.id(), 20L, 1, "READY", "other body"));

        assertThatThrownBy(() -> service.accept(draft.id(), otherCandidate.id()))
                .isInstanceOf(AiParagraphCandidateException.class)
                .hasMessageContaining("Candidate does not belong to this draft");

        assertThat(candidateRepository.findById(otherCandidate.id())).map(AiParagraphCandidate::status).contains("READY");
        assertThat(draftNodeRepository.findByDraftId(otherDraft.id())).singleElement()
                .extracting(DraftNode::content)
                .isEqualTo("");
    }

    @Test
    void lockedOrDeletedTargetsAreRejected() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "Draft", List.of(
                new DraftBlockUpdateRequest("BODY_PARAGRAPH", "", 30)
        ));
        draftNodeRepository.nodes = List.of(
                draftNode(10L, draft.id(), "BODY", "Locked", "", 30, "LOCKED"),
                draftNode(11L, draft.id(), "BODY", "Deleted", "", 40, "DELETED")
        );
        AiParagraphCandidate locked = candidateRepository.insert(candidate(draft.id(), 10L, 1, "READY", "locked text"));
        AiParagraphCandidate deleted = candidateRepository.insert(candidate(draft.id(), 11L, 2, "READY", "deleted text"));

        assertThatThrownBy(() -> service.accept(draft.id(), locked.id()))
                .isInstanceOf(AiParagraphCandidateException.class)
                .hasMessageContaining("Target node cannot be accepted");
        assertThatThrownBy(() -> service.accept(draft.id(), deleted.id()))
                .isInstanceOf(AiParagraphCandidateException.class)
                .hasMessageContaining("Target node cannot be accepted");

        assertThat(candidateRepository.findById(locked.id())).map(AiParagraphCandidate::status).contains("READY");
        assertThat(candidateRepository.findById(deleted.id())).map(AiParagraphCandidate::status).contains("READY");
    }

    private static AiParagraphCandidate candidate(long draftId, Long targetNodeId, int sectionIndex, String status, String text) {
        return new AiParagraphCandidate(
                0L,
                draftId,
                targetNodeId,
                "BODY",
                "Body",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                null,
                sectionIndex,
                "Section " + sectionIndex,
                List.of("point"),
                "instruction",
                text,
                "",
                status,
                "",
                "",
                null,
                null,
                Instant.parse("2026-06-02T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z")
        );
    }

    private static DraftNode draftNode(long id, long draftId, String role, String title, String content, int sortOrder, String status) {
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
                status,
                DraftNodeFormatOverride.empty(),
                DraftNodeMetadata.empty(),
                Instant.parse("2026-06-02T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z")
        );
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

    private static final class FixedCurrentUserProvider extends CurrentUserProvider {
        @Override
        public CurrentUser currentUser() {
            return new CurrentUser(5L, "drafter", "Drafter", 1L, "Office", List.of("DRAFTER"));
        }
    }

    private static final class InMemoryDraftRepository implements DraftRepository {
        private final List<DraftDetailDto> drafts = new ArrayList<>();
        private DraftDetailDto draft;
        private long id = 1;
        private int replaceCalls;

        @Override
        public DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks) {
            DraftDetailDto created = new DraftDetailDto(id++, documentTypeCode, title, "DRAFT", 9L, toDtos(blocks));
            drafts.add(created);
            draft = created;
            return created;
        }

        @Override
        public DraftDetailDto findById(long id) {
            return drafts.stream()
                    .filter(candidate -> candidate.id() == id)
                    .findFirst()
                    .orElseThrow(() -> new DraftNotFoundException(id));
        }

        @Override
        public DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks) {
            replaceCalls++;
            DraftDetailDto existing = findById(id);
            DraftDetailDto updated = new DraftDetailDto(
                    id,
                    existing.documentTypeCode(),
                    existing.title(),
                    existing.status(),
                    existing.templateVersionId(),
                    toDtos(blocks),
                    existing.nodes()
            );
            for (int i = 0; i < drafts.size(); i++) {
                if (drafts.get(i).id() == id) {
                    drafts.set(i, updated);
                    break;
                }
            }
            draft = updated;
            return updated;
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
            Optional<DraftNode> existing = findByDraftId(draftId).stream()
                    .filter(node -> node.id() == nodeId)
                    .findFirst();
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
                            node.metadata(),
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

    private static final class InMemoryCandidateRepository implements AiParagraphCandidateRepository {
        private final List<AiParagraphCandidate> candidates = new ArrayList<>();
        private long id = 1;

        @Override
        public AiParagraphCandidate insert(AiParagraphCandidate candidate) {
            AiParagraphCandidate inserted = copy(candidate, id++, candidate.status(), candidate.candidateText(), candidate.acceptedBy());
            candidates.add(inserted);
            return inserted;
        }

        @Override
        public List<AiParagraphCandidate> findByDraftId(long draftId) {
            return candidates.stream()
                    .filter(candidate -> candidate.draftId() == draftId)
                    .sorted(Comparator.comparing(AiParagraphCandidate::sectionIndex).thenComparing(AiParagraphCandidate::id))
                    .toList();
        }

        @Override
        public Optional<AiParagraphCandidate> findById(long candidateId) {
            return candidates.stream()
                    .filter(candidate -> candidate.id() == candidateId)
                    .findFirst();
        }

        @Override
        public Optional<AiParagraphCandidate> updateTextAndStatus(long candidateId, String text, String status, String digest) {
            return replace(candidateId, candidate -> copy(candidate, candidate.id(), status, text, candidate.acceptedBy()));
        }

        @Override
        public Optional<AiParagraphCandidate> updateStatusAndError(long candidateId, String status, String errorCode, String errorMessage) {
            return replace(candidateId, candidate -> new AiParagraphCandidate(
                    candidate.id(),
                    candidate.draftId(),
                    candidate.targetNodeId(),
                    candidate.targetNodeRole(),
                    candidate.targetNodeTitle(),
                    candidate.outlineTraceId(),
                    candidate.paragraphTraceId(),
                    candidate.sectionIndex(),
                    candidate.heading(),
                    candidate.points(),
                    candidate.instructionSummary(),
                    candidate.candidateText(),
                    candidate.candidateTextDigest(),
                    status,
                    errorCode,
                    errorMessage,
                    candidate.acceptedAt(),
                    candidate.acceptedBy(),
                    candidate.createdAt(),
                    candidate.updatedAt()
            ));
        }

        @Override
        public Optional<AiParagraphCandidate> markAccepted(long candidateId, UUID paragraphTraceId, long acceptedBy) {
            return replace(candidateId, candidate -> new AiParagraphCandidate(
                    candidate.id(),
                    candidate.draftId(),
                    candidate.targetNodeId(),
                    candidate.targetNodeRole(),
                    candidate.targetNodeTitle(),
                    candidate.outlineTraceId(),
                    paragraphTraceId,
                    candidate.sectionIndex(),
                    candidate.heading(),
                    candidate.points(),
                    candidate.instructionSummary(),
                    candidate.candidateText(),
                    candidate.candidateTextDigest(),
                    "ACCEPTED",
                    candidate.errorCode(),
                    candidate.errorMessage(),
                    Instant.parse("2026-06-02T01:00:00Z"),
                    acceptedBy,
                    candidate.createdAt(),
                    candidate.updatedAt()
            ));
        }

        @Override
        public void delete(long candidateId) {
            candidates.removeIf(candidate -> candidate.id() == candidateId);
        }

        private Optional<AiParagraphCandidate> replace(long candidateId, CandidateUpdater updater) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).id() == candidateId) {
                    AiParagraphCandidate updated = updater.update(candidates.get(i));
                    candidates.set(i, updated);
                    return Optional.of(updated);
                }
            }
            return Optional.empty();
        }

        private AiParagraphCandidate copy(
                AiParagraphCandidate candidate,
                long id,
                String status,
                String text,
                Long acceptedBy
        ) {
            return new AiParagraphCandidate(
                    id,
                    candidate.draftId(),
                    candidate.targetNodeId(),
                    candidate.targetNodeRole(),
                    candidate.targetNodeTitle(),
                    candidate.outlineTraceId(),
                    candidate.paragraphTraceId(),
                    candidate.sectionIndex(),
                    candidate.heading(),
                    candidate.points(),
                    candidate.instructionSummary(),
                    text,
                    candidate.candidateTextDigest(),
                    status,
                    candidate.errorCode(),
                    candidate.errorMessage(),
                    candidate.acceptedAt(),
                    acceptedBy,
                    candidate.createdAt(),
                    candidate.updatedAt()
            );
        }
    }

    @FunctionalInterface
    private interface CandidateUpdater {
        AiParagraphCandidate update(AiParagraphCandidate candidate);
    }
}
