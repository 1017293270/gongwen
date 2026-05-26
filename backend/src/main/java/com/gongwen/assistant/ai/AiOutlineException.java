package com.gongwen.assistant.ai;

public class AiOutlineException extends RuntimeException {
    private final String errorCode;

    public AiOutlineException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
