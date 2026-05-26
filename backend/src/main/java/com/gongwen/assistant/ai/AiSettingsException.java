package com.gongwen.assistant.ai;

public class AiSettingsException extends RuntimeException {
    private final String errorCode;

    public AiSettingsException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
