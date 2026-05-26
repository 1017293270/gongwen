package com.gongwen.assistant.ai;

public class ModelAdapterException extends RuntimeException {
    private final String errorCode;

    public ModelAdapterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
