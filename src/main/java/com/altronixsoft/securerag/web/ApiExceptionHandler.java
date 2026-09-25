package com.altronixsoft.securerag.web;

import com.altronixsoft.securerag.service.exception.AnswerGenerationFailedException;
import com.altronixsoft.securerag.service.exception.DocumentNotFoundException;
import com.altronixsoft.securerag.service.exception.DocumentTooLargeException;
import com.altronixsoft.securerag.service.exception.GroupNotAllowedException;
import com.altronixsoft.securerag.service.exception.IngestionFailedException;
import com.altronixsoft.securerag.service.exception.UnreadableDocumentException;
import com.altronixsoft.securerag.service.exception.UnsupportedDocumentException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;

import java.util.List;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 {@link ProblemDetail} responses. Every {@code detail} is a fixed,
 * caller-safe text: exception messages, SQL, parser output and document text go to the log only.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own exceptions (405, 415, malformed
 * JSON, missing parameters, upload too large) keep their proper status instead of falling into the
 * catch-all 500.
 */
@Slf4j
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail documentNotFound(DocumentNotFoundException e) {
        log.debug("Document {} not found or not visible", e.getDocumentId());
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Document not found");
    }

    @ExceptionHandler(UnsupportedDocumentException.class)
    ProblemDetail unsupportedDocument(UnsupportedDocumentException e) {
        log.info("Rejected upload of unsupported type {}", e.getDocumentType());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported document type. Upload PDF, DOCX, Markdown or plain text.");
    }

    @ExceptionHandler(UnreadableDocumentException.class)
    ProblemDetail unreadableDocument(UnreadableDocumentException e) {
        log.info("Rejected unreadable upload: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "The document has no readable text. It may be empty, encrypted, corrupted or a scanned image.");
    }

    @ExceptionHandler(DocumentTooLargeException.class)
    ProblemDetail documentTooLarge(DocumentTooLargeException e) {
        log.info("Rejected upload: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
                "The document contains more text than allowed");
    }

    @ExceptionHandler(GroupNotAllowedException.class)
    ProblemDetail groupNotAllowed(GroupNotAllowedException e) {
        log.info("Rejected sharing with group {}", e.getGroup());
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "A document can only be shared with groups you belong to");
    }

    @ExceptionHandler(IngestionFailedException.class)
    ProblemDetail ingestionFailed(IngestionFailedException e) {
        log.error("Ingestion of document {} failed", e.getDocumentId(), e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "The document could not be processed");
        problem.setProperty("documentId", e.getDocumentId());
        return problem;
    }

    @ExceptionHandler(AnswerGenerationFailedException.class)
    ProblemDetail answerGenerationFailed(AnswerGenerationFailedException e) {
        log.error("Answer generation failed: {}", e.getMessage(), e.getCause());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The assistant could not answer right now. Please try again later.");
    }

    /**
     * Anything not handled above. Security exceptions are rethrown so Spring Security still answers
     * 401/403 itself. A client that disconnected mid-response (closed tab during a long answer) gets
     * nothing: the connection is gone, and it is not a server error. Everything else is a 500 with no
     * internal detail.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
            log.debug("Client disconnected: {}", e.getMessage());
            return null;
        }
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
    }

    /**
     * Bean Validation errors ({@code @Valid}): 400 plus one entry per violation, so a client can show
     * which input to fix. Class-level constraints have no field and are reported under the object name.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getAllErrors().stream()
                .map(error -> Map.of(
                        "field", error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = e.getBody();
        problem.setDetail("Request validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

}
