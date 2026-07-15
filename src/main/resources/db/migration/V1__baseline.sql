-- Baseline schema for one-api-java (PostgreSQL compatible).
-- Generated from legacy JDBC schema and SchemaManager migration.

CREATE TABLE IF NOT EXISTS vendors (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    status INTEGER DEFAULT 1,
    "group" TEXT,
    priority INTEGER DEFAULT 0,
    created_time BIGINT,
    base_url TEXT,
    api_key TEXT,
    balance_credential TEXT,
    meta TEXT
);

CREATE TABLE IF NOT EXISTS instances (
    id SERIAL PRIMARY KEY,
    model_name TEXT NOT NULL,
    status INTEGER DEFAULT 1,
    upstream_model TEXT,
    vendor_id INTEGER REFERENCES vendors(id),
    created_time BIGINT,
    meta TEXT,
    pref REAL DEFAULT 0.5,
    layer TEXT DEFAULT 'payg'
);

CREATE TABLE IF NOT EXISTS virtual_models (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    match TEXT
);

CREATE TABLE IF NOT EXISTS model_catalog (
    name TEXT PRIMARY KEY,
    capabilities TEXT,
    context_window INTEGER,
    input_price REAL,
    output_price REAL,
    reference_notes TEXT
);

-- Reset sequences to max existing id (matches legacy SchemaManager behavior)
SELECT setval('vendors_id_seq', COALESCE((SELECT MAX(id) FROM vendors), 0));
SELECT setval('instances_id_seq', COALESCE((SELECT MAX(id) FROM instances), 0));
SELECT setval('virtual_models_id_seq', COALESCE((SELECT MAX(id) FROM virtual_models), 0));
