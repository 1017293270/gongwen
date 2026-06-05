package com.gongwen.assistant.ai.candidate;

import com.gongwen.assistant.draft.DraftBlockUpdateRequest;
import com.gongwen.assistant.draft.DraftDetailDto;
import com.gongwen.assistant.draft.DraftRepository;
import com.gongwen.assistant.draft.DraftService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParagraphCandidateJobServiceTest {
    private final InMemoryDraftRepository draftRepository = new InMemoryDraftRepository();
    private final InMemoryCandidateRepository candidateRepository = new InMemoryCandidateRepository();
    private final AiParagraphCandidateService candidateService = mock(AiParagraphCandidateService.class);
    private final ParagraphCandidateJobService service = new ParagraphCandidateJobService(
            new DraftService(draftRepository),
            candidateService,
            candidateRepository,
            Runnable::run
    );

    @Test
    void streamJobEmitsBatchAndCandidateStateEvents() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "Draft", List.of());
        AiParagraphCandidateDto pending = candidateDto(draft.id(), 11L, "PENDING", "");
        AiParagraphCandidateDto ready = candidateDto(draft.id(), 11L, "READY", "generated");
        when(candidateService.createBatch(draft.id(), batchRequest())).thenReturn(List.of(pending));
        when(candidateService.retryStreaming(eq(draft.id()), eq(11L), any())).thenAnswer(invocation -> {
            Consumer<String> onDelta = invocation.getArgument(2);
            onDelta.accept("generated");
            return ready;
        });

        ParagraphCandidateJobResponse job = service.createJob(draft.id(), batchRequest());
        CapturingEventSender sender = new CapturingEventSender();

        service.streamJobEvents(draft.id(), job.jobId(), sender);

        assertThat(sender.names()).containsExactly(
                "batch_started",
                "candidate_started",
                "candidate_delta",
                "candidate_ready",
                "batch_done"
        );
        assertThat(sender.events.get(2).delta()).isEqualTo("generated");
    }

    @Test
    void cancelMarksPendingAndRetryingCandidatesCancelled() {
        DraftDetailDto draft = draftRepository.createDraft("NOTICE", "Draft", List.of());
        AiParagraphCandidate pending = candidateRepository.insert(candidate(draft.id(), 1, "PENDING"));
        AiParagraphCandidate retrying = candidateRepository.insert(candidate(draft.id(), 2, "RETRYING"));
        AiParagraphCandidate ready = candidateRepository.insert(candidate(draft.id(), 3, "READY"));
        when(candidateService.createBatch(draft.id(), batchRequest())).thenReturn(List.of(
                AiParagraphCandidateDto.from(pending),
                AiParagraphCandidateDto.from(retrying),
                AiParagraphCandidateDto.from(ready)
        ));

        ParagraphCandidateJobResponse job = service.createJob(draft.id(), batchRequest());
        ParagraphCandidateJobResponse cancelled = service.cancel(draft.id(), job.jobId());

        assertThat(cancelled.cancelled()).isTrue();
        assertThat(candidateRepository.findById(pending.id())).map(AiParagraphCandidate::status).contains("CANCELLED");
        assertThat(candidateRepository.findById(retrying.id())).map(AiParagraphCandidate::status).contains("CANCELLED");
        assertThat(candidateRepository.findById(ready.id())).map(AiParagraphCandidate::status).contains("READY");
    }

    private static CreateParagraphCandidateBatchRequest batchRequest() {
        return new CreateParagraphCandidateBatchRequest(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "outline",
                List.of(new ParagraphCandidateSectionRequest(10L, "BODY", "Body", 1, "Section one", List.of("point"), ""))
        );
    }

    private static AiParagraphCandidateDto candidateDto(long draftId, long id, String status, String text) {
        return AiParagraphCandidateDto.from(new AiParagraphCandidate(
                id,
                draftId,
                10L,
                "BODY",
                "Body",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                null,
                1,
                "Section one",
                List.of("point"),
                "outline",
                text,
                "",
                status,
                "",
                "",
                null,
                null,
                Instant.parse("2026-06-02T00:00:00Z"),
                Instant.parse("2026-06-02T00:00:00Z")
        ));
    }

    private static AiParagraphCandidate candidate(long draftId, int sectionIndex, String status) {
        return new AiParagraphCandidate(
                0,
                draftId,
                10L,
                "BODY",
                "Body",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                null,
                sectionIndex,
                "Section " + sectionIndex,
                List.of("point"),
                "outline",
                "",
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

    private static final class CapturingEventSender implements ParagraphCandidateJobEventSender {
        private final List<ParagraphCandidateJobEvent> events = new ArrayList<>();

        @Override
        public void send(ParagraphCandidateJobEvent event) {
            events.add(event);
        }

        private List<String> names() {
            return events.stream().map(ParagraphCandidateJobEvent::event).toList();
        }
    }

    private static final class InMemoryDraftRepository implements DraftRepository {
        private final List<DraftDetailDto> drafts = new ArrayList<>();
        private long id = 1;

        @Override
        public DraftDetailDto createDraft(String documentTypeCode, String title, List<DraftBlockUpdateRequest> blocks) {
            DraftDetailDto draft = new DraftDetailDto(id++, documentTypeCode, title, "DRAFT", null, List.of());
            drafts.add(draft);
            return draft;
        }

        @Override
        public DraftDetailDto findById(long id) {
            return drafts.stream()
                    .filter(draft -> draft.id() == id)
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public DraftDetailDto replaceBlocks(long id, List<DraftBlockUpdateRequest> blocks) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryCandidateRepository implements AiParagraphCandidateRepository {
        private final List<AiParagraphCandidate> candidates = new ArrayList<>();
        private long id = 1;

        @Override
        public AiParagraphCandidate insert(AiParagraphCandidate candidate) {
            AiParagraphCandidate inserted = new AiParagraphCandidate(
                    id++,
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
                    candidate.status(),
                    candidate.errorCode(),
                    candidate.errorMessage(),
                    candidate.acceptedAt(),
                    candidate.acceptedBy(),
                    candidate.createdAt(),
                    candidate.updatedAt()
            );
            candidates.add(inserted);
            return inserted;
        }

        @Override
        public List<AiParagraphCandidate> findByDraftId(long draftId) {
            return candidates.stream()
                    .filter(candidate -> candidate.draftId() == draftId)
                    .sorted(Comparator.comparing(AiParagraphCandidate::sectionIndex))
                    .toList();
        }

        @Override
        public Optional<AiParagraphCandidate> findById(long candidateId) {
            return candidates.stream().filter(candidate -> candidate.id() == candidateId).findFirst();
        }

        @Override
        public Optional<AiParagraphCandidate> updateTextAndStatus(long candidateId, String text, String status, String digest) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
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
    }

    @FunctionalInterface
    private interface CandidateUpdater {
        AiParagraphCandidate update(AiParagraphCandidate candidate);
    }
}
