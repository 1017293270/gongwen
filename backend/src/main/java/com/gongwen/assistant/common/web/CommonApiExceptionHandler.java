package com.gongwen.assistant.common.web;

import com.gongwen.assistant.ai.AiOutlineException;
import com.gongwen.assistant.ai.candidate.AiParagraphCandidateException;
import com.gongwen.assistant.common.api.ApiResponse;
import com.gongwen.assistant.draft.DraftNotFoundException;
import com.gongwen.assistant.draft.node.DraftNodeException;
import com.gongwen.assistant.exporting.ExportRecordException;
import com.gongwen.assistant.exporting.WordExportException;
import com.gongwen.assistant.rendering.RenderPreviewException;
import com.gongwen.assistant.material.MaterialUploadException;
import com.gongwen.assistant.template.TemplateException;
import com.gongwen.assistant.quality.QualityCheckException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class CommonApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(CommonApiExceptionHandler.class);

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        log.debug("Invalid request", exception);
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request parameter error. Please retry.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        log.debug("Method not allowed", exception);
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Current operation method is not supported. Please retry.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        log.debug("Uploaded file is too large", exception);
        return error(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Uploaded file is too large. Please compress/reduce it and retry.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation", exception);
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "Data conflict. Please sync latest data and retry.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        log.debug("Response status exception", exception);
        String reason = exception.getReason();
        String message = reason == null || reason.isBlank()
                ? "Request cannot be processed now. Please retry."
                : reason;
        return error(HttpStatus.valueOf(exception.getStatusCode().value()), "REQUEST_FAILED", message);
    }

    @ExceptionHandler(DraftNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDraftNotFound(DraftNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DraftNodeException.class)
    public ResponseEntity<ApiResponse<Void>> handleDraftNodeException(DraftNodeException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "DRAFT_NODE_NOT_FOUND", "DOCUMENT_STRUCTURE_PROFILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DRAFT_TEMPLATE_REQUIRED", "STRUCTURE_MAPPING_REQUIRED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        return error(status, exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(AiOutlineException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiOutlineException(AiOutlineException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "AI_OUTLINE_INSTRUCTION_TOO_LONG",
                 "AI_PARAGRAPH_HEADING_REQUIRED",
                 "AI_PARAGRAPH_INSTRUCTION_TOO_LONG",
                 "AI_PARAGRAPH_NODE_ROLE_UNSUPPORTED",
                 "AI_LOCAL_TARGET_REQUIRED",
                 "AI_LOCAL_OPERATION_REQUIRED",
                 "AI_LOCAL_INSTRUCTION_TOO_LONG",
                 "AI_LOCAL_TARGET_NOT_FOUND",
                 "AI_LOCAL_TARGET_NOT_BODY",
                 "AI_LOCAL_TARGET_EMPTY",
                 "AI_LOCAL_TARGET_LOCKED",
                 "AI_NODE_TARGET_NOT_FOUND" -> HttpStatus.BAD_REQUEST;
            case "AI_MODEL_UNAVAILABLE", "AI_RESPONSE_INVALID" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return error(status, exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(MaterialUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaterialUploadException(MaterialUploadException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(QualityCheckException.class)
    public ResponseEntity<ApiResponse<Void>> handleQualityCheckException(QualityCheckException exception) {
        HttpStatus status = "QUALITY_CHECK_NOT_FOUND".equals(exception.errorCode())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return error(status, exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(AiParagraphCandidateException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiParagraphCandidateException(AiParagraphCandidateException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "AI_CANDIDATE_NOT_FOUND",
                 "AI_CANDIDATE_DRAFT_MISMATCH",
                 "AI_CANDIDATE_TARGET_NOT_FOUND",
                 "AI_CANDIDATE_JOB_NOT_FOUND",
                 "AI_CANDIDATE_JOB_DRAFT_MISMATCH" -> HttpStatus.NOT_FOUND;
            case "AI_CANDIDATE_TARGET_BLOCKED",
                 "AI_CANDIDATE_STATUS_PROTECTED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return error(status, exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(TemplateException.class)
    public ResponseEntity<ApiResponse<Void>> handleTemplateException(TemplateException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "TEMPLATE_NOT_FOUND", "TEMPLATE_PROFILE_NOT_FOUND", "DOCUMENT_STRUCTURE_PROFILE_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return error(status, exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(WordExportException.class)
    public ResponseEntity<ApiResponse<Void>> handleWordExportException(WordExportException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(ExportRecordException.class)
    public ResponseEntity<ApiResponse<Void>> handleExportRecordException(ExportRecordException exception) {
        HttpStatus status = "EXPORT_RECORD_NOT_FOUND".equals(exception.errorCode())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return error(status, exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(RenderPreviewException.class)
    public ResponseEntity<ApiResponse<Void>> handleRenderPreviewException(RenderPreviewException exception) {
        HttpStatus status = switch (exception.errorCode()) {
            case "TEMPLATE_VERSION_NOT_FOUND", "RENDER_PREVIEW_NOT_FOUND", "RENDER_PREVIEW_PAGE_NOT_FOUND" ->
                    HttpStatus.NOT_FOUND;
            case "RENDER_PREVIEW_UNSUPPORTED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.BAD_REQUEST;
        };
        return error(status, exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(Exception exception) {
        return error(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "Permission denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled API error", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "System error occurred while processing request. Please retry later.");
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(errorCode, message));
    }
}
