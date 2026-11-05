package com.example.wallet.common.exception;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.DatabindException;

/**
 * Maps our exceptions to RFC 9457 Problem Details; the base class does the same for Spring MVC's
 * own errors (invalid JSON, missing parameters, 404, 405, …).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler
    ProblemDetail handle(InvalidAmountException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(InsufficientFundsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(UnsupportedCurrencyException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
    }

    @ExceptionHandler
    ProblemDetail handle(RatesUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    /** Anything else is a bug: log it, tell the client nothing. */
    @ExceptionHandler
    ProblemDetail handle(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    /** Spring's 400 for {@code @Valid}, plus which fields are invalid. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ex.getBody()
                .setProperty(
                        "errors",
                        ex.getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .sorted()
                                .toList());
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    /**
     * Spring's 400 for an unreadable body, plus the field when Jackson knows it, e.g. currency
     * {@code "XYZ"}. The parser's own message is never echoed.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (ex.getCause() instanceof DatabindException databind && !databind.getPath().isEmpty()) {
            String field = databind.getPath().getLast().getPropertyName();
            if (field != null) {
                var body = ProblemDetail.forStatusAndDetail(status, "Invalid request content.");
                body.setProperty("errors", List.of(field + ": invalid value"));
                return handleExceptionInternal(ex, body, headers, status, request);
            }
        }
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }
}
