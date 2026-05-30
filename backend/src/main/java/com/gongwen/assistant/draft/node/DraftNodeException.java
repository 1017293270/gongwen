package com.gongwen.assistant.draft.node;

public class DraftNodeException extends RuntimeException {
    private final String errorCode;

    public DraftNodeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
