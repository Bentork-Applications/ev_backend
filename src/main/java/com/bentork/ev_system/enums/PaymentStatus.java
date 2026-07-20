package com.bentork.ev_system.enums;

/**
 * Enum representing payment statuses for orders.
 *
 * All values are stored in LOWERCASE for consistency.
 */
public enum PaymentStatus {

    PENDING("pending"),
    PAID("paid");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Convert a string status to PaymentStatus enum (case-insensitive).
     */
    public static PaymentStatus fromString(String status) {
        if (status == null) {
            return null;
        }

        String normalized = status.toLowerCase().trim();

        switch (normalized) {
            case "pending":
                return PENDING;
            case "paid":
                return PAID;
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

    @Override
    public String toString() {
        return value;
    }
}
