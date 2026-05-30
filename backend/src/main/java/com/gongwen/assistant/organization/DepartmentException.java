package com.gongwen.assistant.organization;

public class DepartmentException extends RuntimeException {
    private final String errorCode;

    public DepartmentException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
