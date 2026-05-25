package com.gongwen.assistant.material;

public class MaterialUploadException extends RuntimeException {
    private final String errorCode;

    public MaterialUploadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
