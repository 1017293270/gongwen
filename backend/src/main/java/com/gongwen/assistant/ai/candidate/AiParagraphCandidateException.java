package com.gongwen.assistant.ai.candidate;

public class AiParagraphCandidateException extends RuntimeException {
    private final String errorCode;

    public AiParagraphCandidateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
