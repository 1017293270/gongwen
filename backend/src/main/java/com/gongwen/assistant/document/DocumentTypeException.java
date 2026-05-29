package com.gongwen.assistant.document;

public class DocumentTypeException extends RuntimeException {
    private final String errorCode;

    public DocumentTypeException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
