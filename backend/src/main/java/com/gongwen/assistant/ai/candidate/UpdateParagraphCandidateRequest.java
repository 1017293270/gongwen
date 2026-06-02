package com.gongwen.assistant.ai.candidate;

public record UpdateParagraphCandidateRequest(String candidateText) {
    public UpdateParagraphCandidateRequest {
        candidateText = candidateText == null ? "" : candidateText.strip();
    }
}
