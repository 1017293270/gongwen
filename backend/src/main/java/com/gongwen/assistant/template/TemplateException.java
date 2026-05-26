package com.gongwen.assistant.template;

public class TemplateException extends RuntimeException {
    private final String errorCode;

    public TemplateException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TemplateException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
