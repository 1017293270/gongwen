package com.gongwen.assistant.exporting;

public class WordExportException extends RuntimeException {
    private final String errorCode;

    public WordExportException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
