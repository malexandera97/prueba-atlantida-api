CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    action VARCHAR(30) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    http_status INTEGER NOT NULL,
    message VARCHAR(500),
    username VARCHAR(100) NOT NULL,
    client_ip VARCHAR(64),
    user_agent VARCHAR(255),
    source_account_id BIGINT,
    destination_account_id BIGINT,
    amount NUMERIC(18,2),
    idempotency_key VARCHAR(100),
    transfer_id BIGINT,
    CONSTRAINT chk_audit_outcome CHECK (outcome IN ('EXITOSA', 'REINTENTO', 'RECHAZADA')),
    CONSTRAINT fk_audit_transfer FOREIGN KEY (transfer_id) REFERENCES transfers(id)
);

CREATE INDEX ix_audit_log_created ON audit_log (created_at DESC, id DESC);
CREATE INDEX ix_audit_log_source ON audit_log (source_account_id);
CREATE INDEX ix_audit_log_destination ON audit_log (destination_account_id);
CREATE INDEX ix_audit_log_outcome ON audit_log (outcome);
