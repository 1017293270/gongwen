package com.gongwen.assistant.ai.candidate;

import java.util.UUID;

public record ParagraphCandidateJobEvent(
        String event,
        UUID jobId,
        Long candidateId,
        String status,
        String errorCode,
        String message,
        String delta
) {
    public ParagraphCandidateJobEvent {
        event = normalize(event);
        status = normalize(status);
        errorCode = normalize(errorCode);
        message = normalize(message);
        delta = delta == null ? "" : delta;
    }

    public static ParagraphCandidateJobEvent batch(UUID jobId, String event) {
        return new ParagraphCandidateJobEvent(event, jobId, null, "", "", "", "");
    }

    public static ParagraphCandidateJobEvent candidate(UUID jobId, long candidateId, String event, String status) {
        return new ParagraphCandidateJobEvent(event, jobId, candidateId, status, "", "", "");
    }

    public static ParagraphCandidateJobEvent candidateDelta(UUID jobId, long candidateId, String delta) {
        return new ParagraphCandidateJobEvent("candidate_delta", jobId, candidateId, "STREAMING", "", "", delta);
    }

    public static ParagraphCandidateJobEvent candidateError(
            UUID jobId,
            long candidateId,
            String status,
            String errorCode,
            String message
    ) {
        return new ParagraphCandidateJobEvent("candidate_error", jobId, candidateId, status, errorCode, message, "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
