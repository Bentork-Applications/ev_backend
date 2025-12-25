package com.bentork.ev_system.exception;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.persistence.EntityNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = Logger.getLogger(GlobalExceptionHandler.class.getName());

    // -------- ONE-LINE LOG METHOD -----------
    private void logLine(Exception ex) {
        log.severe(ex.getClass().getSimpleName() + " → " + ex.getMessage());
    }

    private ErrorResponse build(HttpStatus status, String msg) {
        return new ErrorResponse(status.value(), msg);
    }

    // -------- 400 ERRORS ----------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
        logLine(ex);
        String msg = ex.getBindingResult().getFieldError().getDefaultMessage();
        return new ResponseEntity<>(build(HttpStatus.BAD_REQUEST, msg), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> bind(BindException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.BAD_REQUEST, "Binding error"), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> missing(MissingServletRequestParameterException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.BAD_REQUEST,
                "Missing parameter: " + ex.getParameterName()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ErrorResponse> missingPath(MissingPathVariableException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.BAD_REQUEST,
                "Missing path variable: " + ex.getVariableName()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.BAD_REQUEST, "Malformed JSON"), HttpStatus.BAD_REQUEST);
    }

    // -------- 401 / 403 ----------
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> badCredentials(BadCredentialsException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.UNAUTHORIZED,
                "Invalid username or password"), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(AccessDeniedException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.FORBIDDEN, "Access Denied"), HttpStatus.FORBIDDEN);
    }

    // -------- 404 ----------
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> noHandler(NoHandlerFoundException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.NOT_FOUND,
                "Invalid endpoint: " + ex.getRequestURL()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> entity(EntityNotFoundException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.NOT_FOUND, ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    // -------- 405 ----------
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> method(HttpRequestMethodNotSupportedException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP Method Not Allowed"), HttpStatus.METHOD_NOT_ALLOWED);
    }

    // -------- DATABASE ----------
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> data(DataAccessException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Database access error"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Database constraint violation"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ErrorResponse> sql(SQLException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.INTERNAL_SERVER_ERROR,
                "SQL error occurred"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // -------- FILE / IO ----------
    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> io(IOException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.INTERNAL_SERVER_ERROR,
                "I/O or hardware error"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(FileNotFoundException.class)
    public ResponseEntity<ErrorResponse> file(FileNotFoundException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.NOT_FOUND,
                "Requested file not found"), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EOFException.class)
    public ResponseEntity<ErrorResponse> eof(EOFException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.BAD_REQUEST,
                "Unexpected end of input"), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> upload(MaxUploadSizeExceededException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.PAYLOAD_TOO_LARGE,
                "File size exceeds maximum limit"), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    // -------- JAVA RUNTIME ----------
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponse> npe(NullPointerException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Null pointer encountered"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegal(IllegalArgumentException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.BAD_REQUEST,
                ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> illegalState(IllegalStateException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> runtime(RuntimeException ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Runtime error"), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // -------- GENERIC ----------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> general(Exception ex) {
        logLine(ex);
        return new ResponseEntity<>(build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error"), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
