package com.bentork.ev_system.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

//   Global Exception Handler (Client-Side Errors Only)

//   Handles 4 HTTP errors such as validation, unauthorized access,
//   resource not found, and bad request issues.

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 400 - Bad Request: Invalid input validation (from @Valid annotations)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationErrors(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("messages", fieldErrors);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * 400 - Bad Request: Missing required query parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingParams(MissingServletRequestParameterException ex, WebRequest request) {
        log.warn("Missing parameter: {}", ex.getParameterName());
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing Request Parameter",
                "Required parameter '" + ex.getParameterName() + "' is missing.", request);
    }

    /**
     * 400 - Bad Request: Missing required headers
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Object> handleMissingHeader(MissingRequestHeaderException ex, WebRequest request) {
        log.warn("Missing request header: {}", ex.getHeaderName());
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing Request Header",
                "Required header '" + ex.getHeaderName() + "' is missing.", request);
    }

    /**
     * 401 - Unauthorized: Invalid credentials (login / JWT)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        log.warn("Invalid credentials attempt: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid username or password", request);
    }

    /**
     * 403 - Forbidden: Access denied (role-based)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Access Denied",
                "You do not have permission to perform this action.", request);
    }

    /**
     * 404 - Resource Not Found
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage(), request);
    }

    /**
     * Utility method to build a structured error response.
     */
    private ResponseEntity<Object> buildResponse(HttpStatus status, String error, String message, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", error);
        body.put("message", message);
        body.put("path", request.getDescription(false));
        return new ResponseEntity<>(body, status);
    }
}
