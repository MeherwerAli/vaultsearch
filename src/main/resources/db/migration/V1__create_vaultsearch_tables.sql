CREATE TABLE vaults (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    vault_key VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    root_fingerprint VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);

CREATE TABLE notes (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    vault_id VARCHAR(36) NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    relative_path_hash BINARY(32) GENERATED ALWAYS AS (UNHEX(SHA2(relative_path, 256))) STORED,
    title VARCHAR(512) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    projection_hash VARCHAR(64) NOT NULL,
    modified_at DATETIME(6) NOT NULL,
    index_state VARCHAR(32) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_notes_vault FOREIGN KEY (vault_id) REFERENCES vaults(id),
    CONSTRAINT uq_notes_vault_path UNIQUE (vault_id, relative_path_hash)
);

CREATE TABLE index_tasks (
    note_id VARCHAR(64) NOT NULL PRIMARY KEY,
    operation VARCHAR(16) NOT NULL,
    expected_hash VARCHAR(64),
    payload_json LONGTEXT,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error_code VARCHAR(128),
    last_error_hash VARCHAR(64),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_tasks_note FOREIGN KEY (note_id) REFERENCES notes(id)
);

CREATE INDEX idx_notes_vault_deleted ON notes(vault_id, deleted);
CREATE INDEX idx_tasks_status_retry ON index_tasks(status, next_attempt_at);
