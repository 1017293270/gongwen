package com.gongwen.assistant.organization;

public class UserAdminException extends RuntimeException {
    private final String errorCode;

    public UserAdminException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
