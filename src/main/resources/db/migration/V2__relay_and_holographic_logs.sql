-- relay_logs and holographic_logs tables for JPA async logging

CREATE TABLE IF NOT EXISTS relay_logs (
    id BIGSERIAL PRIMARY KEY,
    ts TIMESTAMP NOT NULL,
    channel_id INTEGER,
    base_url TEXT,
    token_name TEXT,
    user_id INTEGER,
    model_orig TEXT,
    model_real TEXT,
    stream INTEGER DEFAULT 0,
    body_size INTEGER DEFAULT 0,
    code INTEGER DEFAULT 0,
    resp_size INTEGER DEFAULT 0,
    tokens INTEGER DEFAULT 0,
    latency_ms BIGINT DEFAULT 0,
    err TEXT
);

CREATE TABLE IF NOT EXISTS holographic_logs (
    id BIGSERIAL PRIMARY KEY,
    request_id TEXT NOT NULL UNIQUE,
    timestamp_ms BIGINT NOT NULL,
    requested_model TEXT,
    final_status TEXT,
    final_http_code INTEGER,
    total_latency_ms BIGINT,
    total_tokens INTEGER,
    data JSONB
);

SELECT setval('relay_logs_id_seq', COALESCE((SELECT MAX(id) FROM relay_logs), 0));
SELECT setval('holographic_logs_id_seq', COALESCE((SELECT MAX(id) FROM holographic_logs), 0));
