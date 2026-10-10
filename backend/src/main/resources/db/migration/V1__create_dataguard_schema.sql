-- Idempotent initial schema. Existing records and tables are preserved.
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    password VARCHAR(255),
    role VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    technology VARCHAR(255),
    user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP,
    last_quality_score INTEGER
);

CREATE TABLE IF NOT EXISTS reviews (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT REFERENCES projects(id),
    overall_score INTEGER,
    security_score INTEGER,
    quality_score INTEGER,
    architecture_score INTEGER,
    status VARCHAR(255),
    review_date TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS findings (
    id BIGSERIAL PRIMARY KEY,
    review_id BIGINT REFERENCES reviews(id),
    category VARCHAR(255),
    severity VARCHAR(255),
    source VARCHAR(255),
    rule_id VARCHAR(255),
    title VARCHAR(255),
    description VARCHAR(2000),
    file_path VARCHAR(255),
    line_number INTEGER,
    evidence VARCHAR(2000),
    recommendation VARCHAR(2000),
    status VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS ai_explanations (
    id BIGSERIAL PRIMARY KEY,
    finding_id BIGINT UNIQUE REFERENCES findings(id),
    explanation TEXT,
    impact TEXT,
    recommended_action TEXT
);

CREATE TABLE IF NOT EXISTS github_review_progress (
    id VARCHAR(255) PRIMARY KEY,
    idempotency_key VARCHAR(500) NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    owner VARCHAR(255) NOT NULL,
    repository VARCHAR(255) NOT NULL,
    branch VARCHAR(255),
    base_sha VARCHAR(255),
    commit_sha VARCHAR(255) NOT NULL,
    pull_request_number INTEGER,
    files_json TEXT NOT NULL,
    skipped_json TEXT NOT NULL,
    batch_results_json TEXT NOT NULL,
    next_batch_index INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    active_check_run_id BIGINT,
    parent_review_id BIGINT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS radar_conversations (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS radar_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES radar_conversations(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    content VARCHAR(12000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS revoked_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_radar_conversation_owner_updated ON radar_conversations(owner_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_radar_message_conversation_created ON radar_messages(conversation_id, created_at);
