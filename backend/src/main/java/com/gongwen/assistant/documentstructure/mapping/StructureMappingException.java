package com.gongwen.assistant.documentstructure.mapping;

public class StructureMappingException extends RuntimeException {
    private final String errorCode;

    public StructureMappingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
