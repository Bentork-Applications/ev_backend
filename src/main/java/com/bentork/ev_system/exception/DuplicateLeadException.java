package com.bentork.ev_system.exception;

import lombok.Getter;

/**
 * Thrown when attempting to create or save a Lead whose mobile number
 * or email already exists in the CRM.
 *
 * Handled by {@link GlobalExceptionHandler} and CRM controllers to return
 * HTTP 409 Conflict.
 */
@Getter
public class DuplicateLeadException extends RuntimeException {

    private final String duplicateField; // "phone", "email", or "phone_and_email"
    private final Long existingLeadId;
    private final String existingLeadNumber;

    public DuplicateLeadException(String message) {
        this(message, null, null, null);
    }

    public DuplicateLeadException(String message, String duplicateField, Long existingLeadId, String existingLeadNumber) {
        super(message);
        this.duplicateField = duplicateField;
        this.existingLeadId = existingLeadId;
        this.existingLeadNumber = existingLeadNumber;
    }
}
