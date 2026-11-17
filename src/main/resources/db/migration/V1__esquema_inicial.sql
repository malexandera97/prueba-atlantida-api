CREATE TABLE accounts (
    id BIGINT PRIMARY KEY,
    holder_name VARCHAR(100) NOT NULL,
    balance NUMERIC(18,2) NOT NULL CHECK (balance >= 0)
);

CREATE TABLE transfers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_account_id BIGINT NOT NULL,
    destination_account_id BIGINT NOT NULL,
    amount NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_source FOREIGN KEY (source_account_id)
        REFERENCES accounts(id),
    CONSTRAINT fk_destination FOREIGN KEY (destination_account_id)
        REFERENCES accounts(id),
    CONSTRAINT chk_different_accounts
        CHECK (source_account_id <> destination_account_id)
);

INSERT INTO accounts (id, holder_name, balance) VALUES
(1001, 'Juan Pérez', 5000.00),
    (1002, 'María López', 2000.00),
    (1003, 'Carlos Gómez', 10000.00);
