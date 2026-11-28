package com.bentork.ev_system.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs on application startup to ensure all required database columns exist.
 * This handles cases where Hibernate's ddl-auto=update fails to add new columns
 * (e.g., due to DB permissions, dialect issues, or silent failures).
 *
 * Each migration is idempotent — safe to run multiple times.
 */
@Component
@Order(1) // Run early in startup
public class DatabaseMigrationConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationConfig.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationConfig(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(String... args) {
        log.info("Running database migration checks...");

        // ==================== ORDERS TABLE MIGRATIONS ====================
        addColumnIfNotExists("orders", "delivered_at", "DATETIME NULL");
        addColumnIfNotExists("orders", "dispatched_at", "DATETIME NULL");

        // ==================== LEADS TABLE MIGRATIONS ====================
        addColumnIfNotExists("leads", "previous_status", "VARCHAR(255) NULL");
        addColumnIfNotExists("leads", "lost_reason", "TEXT NULL");
        addColumnIfNotExists("leads", "lost_approval_status", "VARCHAR(50) NULL");
        addColumnIfNotExists("leads", "lost_requested_by_admin_id", "BIGINT NULL");
        addColumnIfNotExists("leads", "lost_requested_at", "DATETIME NULL");
        addColumnIfNotExists("leads", "lost_approved_by_admin_id", "BIGINT NULL");
        addColumnIfNotExists("leads", "lost_approved_at", "DATETIME NULL");
        addColumnIfNotExists("leads", "lost_revoked_by_admin_id", "BIGINT NULL");
        addColumnIfNotExists("leads", "lost_revoked_at", "DATETIME NULL");
        addColumnIfNotExists("leads", "lost_revoke_reason", "TEXT NULL");

        // ==================== OPPORTUNITIES TABLE MIGRATIONS ====================
        addColumnIfNotExists("opportunities", "previous_stage", "VARCHAR(255) NULL");
        addColumnIfNotExists("opportunities", "lost_approval_status", "VARCHAR(50) NULL");
        addColumnIfNotExists("opportunities", "lost_requested_by_admin_id", "BIGINT NULL");
        addColumnIfNotExists("opportunities", "lost_requested_at", "DATETIME NULL");
        addColumnIfNotExists("opportunities", "lost_approved_by_admin_id", "BIGINT NULL");
        addColumnIfNotExists("opportunities", "lost_approved_at", "DATETIME NULL");
        addColumnIfNotExists("opportunities", "lost_revoked_by_admin_id", "BIGINT NULL");
        addColumnIfNotExists("opportunities", "lost_revoked_at", "DATETIME NULL");
        addColumnIfNotExists("opportunities", "lost_revoke_reason", "TEXT NULL");

        // ==================== DUPLICATE LEAD PREVENTION INDEXES & CONSTRAINTS ====================
        normalizeBlankValuesToNull("leads", "phone");
        normalizeBlankValuesToNull("leads", "email");
        normalizeBlankValuesToNull("sales_contacts", "phone");
        normalizeBlankValuesToNull("sales_contacts", "email");

        ensureUniqueOrRegularIndex("leads", "phone", "uk_lead_phone", "idx_lead_phone");
        ensureUniqueOrRegularIndex("leads", "email", "uk_lead_email", "idx_lead_email");
        ensureUniqueOrRegularIndex("sales_contacts", "phone", "uk_sales_contact_phone", "idx_sales_contact_phone");
        ensureUniqueOrRegularIndex("sales_contacts", "email", "uk_sales_contact_email", "idx_sales_contact_email");
        ensureIndexIfNotExists("leads", "lost_approval_status", "idx_lead_lost_approval");
        ensureIndexIfNotExists("opportunities", "lost_approval_status", "idx_opportunity_lost_approval");

        log.info("Database migration checks completed.");
    }

    private void normalizeBlankValuesToNull(String tableName, String columnName) {
        try {
            String sql = "UPDATE " + tableName + " SET " + columnName + " = NULL WHERE TRIM(" + columnName + ") = ''";
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.debug("Skipping blank normalization for {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }

    private void ensureUniqueOrRegularIndex(String tableName, String columnName, String uniqueIndexName, String regularIndexName) {
        try {
            String checkUniqueSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? AND NON_UNIQUE = 0";
            Integer uniqueCount = jdbcTemplate.queryForObject(checkUniqueSql, Integer.class, tableName, columnName);
            if (uniqueCount != null && uniqueCount > 0) {
                return;
            }

            String dupCheckSql = "SELECT COUNT(*) FROM (SELECT " + columnName + " FROM " + tableName +
                    " WHERE " + columnName + " IS NOT NULL GROUP BY " + columnName + " HAVING COUNT(*) > 1) t";
            Integer dupCount = jdbcTemplate.queryForObject(dupCheckSql, Integer.class);
            if (dupCount != null && dupCount == 0) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD CONSTRAINT " + uniqueIndexName + " UNIQUE (" + columnName + ")");
                log.info("Added unique constraint {} on {}.{}", uniqueIndexName, tableName, columnName);
            } else {
                log.warn("Pre-existing duplicate values found in {}.{} ({} duplicate groups); adding non-unique index {} instead.",
                        tableName, columnName, dupCount, regularIndexName);
                ensureIndexIfNotExists(tableName, columnName, regularIndexName);
            }
        } catch (Exception e) {
            log.warn("Could not ensure unique constraint on {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }

    private void ensureIndexIfNotExists(String tableName, String columnName, String indexName) {
        try {
            String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, tableName, indexName);
            if (count == null || count == 0) {
                jdbcTemplate.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columnName + ")");
                log.info("Created index {} on {}.{}", indexName, tableName, columnName);
            }
        } catch (Exception e) {
            log.warn("Could not create index {} on {}.{}: {}", indexName, tableName, columnName, e.getMessage());
        }
    }

    /**
     * Adds a column to a table if it doesn't already exist.
     * Uses INFORMATION_SCHEMA to check column existence (MySQL compatible).
     *
     * @param tableName  the table to alter
     * @param columnName the column to add
     * @param columnDef  the column definition (e.g., "DATETIME NULL", "VARCHAR(255)")
     */
    private void addColumnIfNotExists(String tableName, String columnName, String columnDef) {
        try {
            String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";

            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, tableName, columnName);

            if (count == null || count == 0) {
                String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDef;
                jdbcTemplate.execute(alterSql);
                log.info("✅ Added missing column: {}.{}", tableName, columnName);
            } else {
                log.debug("Column {}.{} already exists, skipping.", tableName, columnName);
            }
        } catch (Exception e) {
            log.error("❌ Failed to add column {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }
}
