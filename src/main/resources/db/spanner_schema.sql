-- ==============================================================================
-- Google Cloud Spanner Schema Definition: kyc_profile
-- Database: mortgage_db
-- Instance: mltf-spanner
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. Google Standard SQL Dialect (Spanner Default)
-- ------------------------------------------------------------------------------
CREATE TABLE kyc_profile (
    user_id STRING(64) NOT NULL,
    full_name STRING(255) NOT NULL,
    email STRING(255) NOT NULL,
    phone_number STRING(32),
    id_card_number STRING(64),
    id_card_type STRING(32),           -- e.g. NATIONAL_ID, PASSPORT, DRIVERS_LICENSE
    date_of_birth DATE,
    address STRING(500),
    city STRING(100),
    postal_code STRING(20),
    country STRING(100),
    nationality STRING(100),
    occupation STRING(100),
    monthly_income NUMERIC,
    status STRING(32) NOT NULL,         -- PENDING, IN_REVIEW, APPROVED, REJECTED
    risk_score FLOAT64,                 -- e.g. 0.0 to 100.0
    risk_level STRING(32),              -- LOW, MEDIUM, HIGH
    rejection_reason STRING(1000),
    remarks STRING(1000),
    verified_by STRING(64),             -- Supervisor agent / reviewer ID
    verified_at TIMESTAMP OPTIONS (allow_commit_timestamp = true),
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true)
) PRIMARY KEY (user_id);

-- Migration DDL for existing database instances:
-- ALTER TABLE kyc_profile ALTER COLUMN verified_at SET OPTIONS (allow_commit_timestamp = true);

-- Secondary Indexes for query performance
CREATE INDEX idx_kyc_profile_email ON kyc_profile(email);
CREATE INDEX idx_kyc_profile_status ON kyc_profile(status);
CREATE INDEX idx_kyc_profile_id_card_number ON kyc_profile(id_card_number);

-- ------------------------------------------------------------------------------
-- Sample Data Insertion (Google Standard SQL)
-- ------------------------------------------------------------------------------
INSERT INTO kyc_profile (
    user_id, full_name, email, phone_number, id_card_number, id_card_type,
    date_of_birth, address, city, postal_code, country, nationality,
    occupation, monthly_income, status, risk_score, risk_level,
    rejection_reason, remarks, verified_by, verified_at, created_at, updated_at
) VALUES (
    'usr_1001', 'John Doe', 'john.doe@example.com', '+1-555-0199', 'ID-987654321', 'NATIONAL_ID',
    DATE '1988-05-12', '123 Main St, Suite 400', 'New York', '10001', 'USA', 'American',
    'Senior Software Engineer', 12500.00, 'APPROVED', 12.5, 'LOW',
    NULL, 'Identity verified successfully via biometric match', 'agent_supervisor_01', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()
);

INSERT INTO kyc_profile (
    user_id, full_name, email, phone_number, id_card_number, id_card_type,
    date_of_birth, address, city, postal_code, country, nationality,
    occupation, monthly_income, status, risk_score, risk_level,
    rejection_reason, remarks, verified_by, verified_at, created_at, updated_at
) VALUES (
    'usr_1002', 'Jane Smith', 'jane.smith@example.com', '+1-555-0245', 'ID-123456789', 'PASSPORT',
    DATE '1992-11-23', '456 Oak Avenue', 'San Francisco', '94102', 'USA', 'American',
    'Financial Analyst', 9500.00, 'IN_REVIEW', 35.0, 'MEDIUM',
    NULL, 'Income documents pending supervisor signoff', NULL, NULL, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()
);

INSERT INTO kyc_profile (
    user_id, full_name, email, phone_number, id_card_number, id_card_type,
    date_of_birth, address, city, postal_code, country, nationality,
    occupation, monthly_income, status, risk_score, risk_level,
    rejection_reason, remarks, verified_by, verified_at, created_at, updated_at
) VALUES (
    'usr_1003', 'Robert Johnson', 'robert.j@example.com', '+1-555-0378', 'ID-556677889', 'NATIONAL_ID',
    DATE '1985-02-18', '789 Pine Road', 'Chicago', '60601', 'USA', 'American',
    'Consultant', 6000.00, 'REJECTED', 85.0, 'HIGH',
    'Unverified identity documents and high credit risk', 'Documentation expired', 'agent_supervisor_02', CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()
);

-- ------------------------------------------------------------------------------
-- 2. Alternative PostgreSQL Dialect (if Spanner database uses PostgreSQL dialect)
-- ------------------------------------------------------------------------------
/*
CREATE TABLE kyc_profile (
    user_id VARCHAR(64) NOT NULL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number VARCHAR(32),
    id_card_number VARCHAR(64),
    id_card_type VARCHAR(32),
    date_of_birth DATE,
    address VARCHAR(500),
    city VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    nationality VARCHAR(100),
    occupation VARCHAR(100),
    monthly_income NUMERIC,
    status VARCHAR(32) NOT NULL,
    risk_score DOUBLE PRECISION,
    risk_level VARCHAR(32),
    rejection_reason VARCHAR(1000),
    remarks VARCHAR(1000),
    verified_by VARCHAR(64),
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_kyc_profile_email ON kyc_profile(email);
CREATE INDEX idx_kyc_profile_status ON kyc_profile(status);
CREATE INDEX idx_kyc_profile_id_card_number ON kyc_profile(id_card_number);
*/
