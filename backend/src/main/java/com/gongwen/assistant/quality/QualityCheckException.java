package com.gongwen.assistant.quality;

public class QualityCheckException extends RuntimeException {
    private final String errorCode;

    public QualityCheckException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
