package com.gongwen.assistant.ai.candidate;

import com.gongwen.assistant.draft.DraftService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ParagraphCandidateJobService {
    private static final Set<String> CANCELLABLE_STATUSES = Set.of("PENDING", "RETRYING");
    private static final String CANCELLED_ERROR_CODE = "AI_CANDIDATE_JOB_CANCELLED";
    private static final String CANCELLED_MESSAGE = "Paragraph candidate batch job was cancelled";

    private final DraftService draftService;
    private final AiParagraphCandidateService candidateService;
    private final AiParagraphCandidateRepository candidateRepository;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final Map<UUID, JobState> jobs = new ConcurrentHashMap<>();

    public ParagraphCandidateJobService(
            DraftService draftService,
            AiParagraphCandidateService candidateService,
            AiParagraphCandidateRepository candidateRepository
    ) {
        this(
                draftService,
                candidateService,
                candidateRepository,
                Executors.newCachedThreadPool()
        );
    }

    ParagraphCandidateJobService(
            DraftService draftService,
            AiParagraphCandidateService candidateService,
            AiParagraphCandidateRepository candidateRepository,
            Executor executor
    ) {
        this.draftService = draftService;
        this.candidateService = candidateService;
        this.candidateRepository = candidateRepository;
        this.executor = executor;
        this.ownedExecutor = executor instanceof ExecutorService executorService ? executorService : null;
    }

    public ParagraphCandidateJobResponse createJob(
            long draftId,
            CreateParagraphCandidateBatchRequest request
    ) {
        List<Long> candidateIds = candidateService.createBatch(draftId, request).stream()
                .map(AiParagraphCandidateDto::id)
                .toList();
        UUID jobId = UUID.randomUUID();
        jobs.put(jobId, new JobState(draftId, candidateIds));
        return new ParagraphCandidateJobResponse(jobId, candidateIds, false);
    }

    public SseEmitter streamJob(long draftId, UUID jobId) {
        draftService.getDraft(draftId);
        JobState job = job(jobId);
        if (job.draftId() != draftId) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_JOB_DRAFT_MISMATCH",
                    "Candidate job does not belong to this draft"
            );
        }

        SseEmitter emitter = new SseEmitter(0L);
        executor.execute(() -> {
            try {
                streamJobEvents(draftId, jobId, event -> sendEvent(emitter, event));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    public ParagraphCandidateJobResponse cancel(long draftId, UUID jobId) {
        draftService.getDraft(draftId);
        JobState job = job(jobId);
        if (job.draftId() != draftId) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_JOB_DRAFT_MISMATCH",
                    "Candidate job does not belong to this draft"
            );
        }
        job.cancel();
        markCancellableCandidates(job);
        return new ParagraphCandidateJobResponse(jobId, job.candidateIds(), true);
    }

    void streamJobEvents(long draftId, UUID jobId, ParagraphCandidateJobEventSender sender) {
        draftService.getDraft(draftId);
        JobState job = job(jobId);
        if (job.draftId() != draftId) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_JOB_DRAFT_MISMATCH",
                    "Candidate job does not belong to this draft"
            );
        }

        sender.send(ParagraphCandidateJobEvent.batch(jobId, "batch_started"));
        boolean cancelled = false;
        for (Long candidateId : job.candidateIds()) {
            if (job.isCancelled()) {
                cancelled = true;
                break;
            }
            sender.send(ParagraphCandidateJobEvent.candidate(jobId, candidateId, "candidate_started", "RETRYING"));
            try {
                AiParagraphCandidateDto candidate = candidateService.retry(draftId, candidateId);
                if ("ERROR".equals(candidate.status())) {
                    sender.send(ParagraphCandidateJobEvent.candidateError(
                            jobId,
                            candidate.id(),
                            candidate.status(),
                            candidate.errorCode(),
                            candidate.errorMessage()
                    ));
                } else {
                    sender.send(ParagraphCandidateJobEvent.candidate(
                            jobId,
                            candidate.id(),
                            "candidate_ready",
                            candidate.status()
                    ));
                }
            } catch (RuntimeException exception) {
                sender.send(ParagraphCandidateJobEvent.candidateError(
                        jobId,
                        candidateId,
                        "ERROR",
                        "AI_CANDIDATE_JOB_ERROR",
                        exception.getMessage()
                ));
            }
        }
        sender.send(ParagraphCandidateJobEvent.batch(jobId, cancelled ? "batch_cancelled" : "batch_done"));
    }

    @PreDestroy
    void shutdown() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
    }

    private void sendEvent(SseEmitter emitter, ParagraphCandidateJobEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.event())
                    .data(event));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to send paragraph candidate job event", exception);
        }
    }

    private JobState job(UUID jobId) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            throw new AiParagraphCandidateException(
                    "AI_CANDIDATE_JOB_NOT_FOUND",
                    "Candidate job " + jobId + " was not found"
            );
        }
        return job;
    }

    private void markCancellableCandidates(JobState job) {
        for (Long candidateId : job.candidateIds()) {
            candidateRepository.findById(candidateId)
                    .filter(candidate -> candidate.draftId() == job.draftId())
                    .filter(candidate -> CANCELLABLE_STATUSES.contains(candidate.status()))
                    .ifPresent(candidate -> candidateRepository.updateStatusAndError(
                            candidate.id(),
                            "CANCELLED",
                            CANCELLED_ERROR_CODE,
                            CANCELLED_MESSAGE
                    ));
        }
    }

    private record JobState(
            long draftId,
            List<Long> candidateIds,
            AtomicBoolean cancelled
    ) {
        private JobState(long draftId, List<Long> candidateIds) {
            this(draftId, List.copyOf(candidateIds), new AtomicBoolean(false));
        }

        private boolean isCancelled() {
            return cancelled.get();
        }

        private void cancel() {
            cancelled.set(true);
        }
    }
}
