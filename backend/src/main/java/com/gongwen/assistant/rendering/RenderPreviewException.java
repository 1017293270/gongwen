package com.gongwen.assistant.rendering;

public class RenderPreviewException extends RuntimeException {
    private final String errorCode;
    private final DocumentRenderPreviewStatus failureStatus;

    public RenderPreviewException(String errorCode, String message) {
        this(errorCode, message, DocumentRenderPreviewStatus.FAILED, null);
    }

    public RenderPreviewException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, DocumentRenderPreviewStatus.FAILED, cause);
    }

    public RenderPreviewException(String errorCode, String message, DocumentRenderPreviewStatus failureStatus) {
        this(errorCode, message, failureStatus, null);
    }

    public RenderPreviewException(
            String errorCode,
            String message,
            DocumentRenderPreviewStatus failureStatus,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
        this.failureStatus = failureStatus == null ? DocumentRenderPreviewStatus.FAILED : failureStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public DocumentRenderPreviewStatus failureStatus() {
        return failureStatus;
    }
}
