-- Migration: Duplicate Lead Prevention (indexes/unique constraints) & Lost Approval/Revoke Workflow
-- Note: DatabaseMigrationConfig also applies these idempotently on application startup.

-- 1. Normalize blank phones/emails to NULL so unique constraints allow multiple NULLs
UPDATE leads SET phone = NULL WHERE TRIM(phone) = '';
UPDATE leads SET email = NULL WHERE TRIM(email) = '';
UPDATE sales_contacts SET phone = NULL WHERE TRIM(phone) = '';
UPDATE sales_contacts SET email = NULL WHERE TRIM(email) = '';

-- 2. Unique constraints & indexes on phone and email
ALTER TABLE leads ADD CONSTRAINT uk_lead_phone UNIQUE (phone);
ALTER TABLE leads ADD CONSTRAINT uk_lead_email UNIQUE (email);
ALTER TABLE sales_contacts ADD CONSTRAINT uk_sales_contact_phone UNIQUE (phone);
ALTER TABLE sales_contacts ADD CONSTRAINT uk_sales_contact_email UNIQUE (email);

-- 3. Lost Approval & Revoke workflow columns on leads
ALTER TABLE leads ADD COLUMN previous_status VARCHAR(255) NULL;
ALTER TABLE leads ADD COLUMN lost_reason TEXT NULL;
ALTER TABLE leads ADD COLUMN lost_approval_status VARCHAR(50) NULL;
ALTER TABLE leads ADD COLUMN lost_requested_by_admin_id BIGINT NULL;
ALTER TABLE leads ADD COLUMN lost_requested_at DATETIME NULL;
ALTER TABLE leads ADD COLUMN lost_approved_by_admin_id BIGINT NULL;
ALTER TABLE leads ADD COLUMN lost_approved_at DATETIME NULL;
ALTER TABLE leads ADD COLUMN lost_revoked_by_admin_id BIGINT NULL;
ALTER TABLE leads ADD COLUMN lost_revoked_at DATETIME NULL;
ALTER TABLE leads ADD COLUMN lost_revoke_reason TEXT NULL;
CREATE INDEX idx_lead_lost_approval ON leads (lost_approval_status);

-- 4. Lost Approval & Revoke workflow columns on opportunities
ALTER TABLE opportunities ADD COLUMN previous_stage VARCHAR(255) NULL;
ALTER TABLE opportunities ADD COLUMN lost_approval_status VARCHAR(50) NULL;
ALTER TABLE opportunities ADD COLUMN lost_requested_by_admin_id BIGINT NULL;
ALTER TABLE opportunities ADD COLUMN lost_requested_at DATETIME NULL;
ALTER TABLE opportunities ADD COLUMN lost_approved_by_admin_id BIGINT NULL;
ALTER TABLE opportunities ADD COLUMN lost_approved_at DATETIME NULL;
ALTER TABLE opportunities ADD COLUMN lost_revoked_by_admin_id BIGINT NULL;
ALTER TABLE opportunities ADD COLUMN lost_revoked_at DATETIME NULL;
ALTER TABLE opportunities ADD COLUMN lost_revoke_reason TEXT NULL;
CREATE INDEX idx_opportunity_lost_approval ON opportunities (lost_approval_status);
