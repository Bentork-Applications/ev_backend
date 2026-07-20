package com.bentork.ev_system.enums;

/**
 * Enum representing production stage statuses.
 *
 * Status flow: PENDING -> IN_PROGRESS -> COMPLETED
 *
 * All values are stored in LOWERCASE for consistency.
 */
public enum ProductionStatus {

    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed");

    private final String value;

    ProductionStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Convert a string status to ProductionStatus enum (case-insensitive).
     */
    public static ProductionStatus fromString(String status) {
        if (status == null) {
            return null;
        }

        String normalized = status.toLowerCase().trim();

        switch (normalized) {
            case "pending":
                return PENDING;
            case "in_progress":
                return IN_PROGRESS;
            case "completed":
                return COMPLETED;
            default:
                return null;
        }
    }

    /**
     * Check if status string matches this enum value (case-insensitive).
     */
    public boolean matches(String status) {
        if (status == null) {
            return false;
        }
        return this.value.equalsIgnoreCase(status.trim());
    }

    /**
     * Validates whether the transition from current to next status is allowed.
     */
    public static boolean isValidTransition(ProductionStatus current, ProductionStatus next) {
        if (current == null || next == null) {
            return false;
        }

        switch (current) {
            case PENDING:
                return next == IN_PROGRESS;
            case IN_PROGRESS:
                return next == COMPLETED;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
