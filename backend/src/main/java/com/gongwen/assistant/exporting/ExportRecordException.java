package com.gongwen.assistant.exporting;

public class ExportRecordException extends RuntimeException {
    private final String errorCode;

    public ExportRecordException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ExportRecordException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
