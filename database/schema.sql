CREATE DATABASE IF NOT EXISTS poker_game CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE poker_game;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(24) NOT NULL UNIQUE,
    password_hash VARBINARY(32) NOT NULL,
    password_salt VARBINARY(16) NOT NULL,
    password_iterations INT NOT NULL,
    account_role VARCHAR(16) NOT NULL DEFAULT 'PLAYER',
    account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    chips BIGINT NOT NULL DEFAULT 10000 CHECK (chips >= 0),
    wins BIGINT NOT NULL DEFAULT 0,
    games_played BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sessions (
    token_hash BINARY(32) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_sessions_user_active (user_id, revoked_at, expires_at)
);

CREATE TABLE IF NOT EXISTS poker_tables (
    id CHAR(36) PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    small_blind BIGINT NOT NULL,
    max_players TINYINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    CONSTRAINT fk_tables_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS hands (
    id CHAR(36) PRIMARY KEY,
    table_id CHAR(36) NOT NULL,
    dealer_seat TINYINT NOT NULL,
    board_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    settled_at TIMESTAMP NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL,
    CONSTRAINT fk_hands_table FOREIGN KEY (table_id) REFERENCES poker_tables(id),
    INDEX idx_hands_table_started (table_id, started_at)
);

CREATE TABLE IF NOT EXISTS hand_players (
    hand_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    seat_no TINYINT NOT NULL,
    starting_stack BIGINT NOT NULL,
    final_stack BIGINT NOT NULL,
    contribution BIGINT NOT NULL,
    result VARCHAR(20) NOT NULL,
    PRIMARY KEY (hand_id, user_id),
    CONSTRAINT fk_hand_players_hand FOREIGN KEY (hand_id) REFERENCES hands(id),
    CONSTRAINT fk_hand_players_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS hand_actions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    hand_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    action_seq INT NOT NULL,
    street VARCHAR(20) NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_actions_hand FOREIGN KEY (hand_id) REFERENCES hands(id),
    CONSTRAINT fk_actions_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uq_hand_action_seq (hand_id, action_seq)
);

CREATE TABLE IF NOT EXISTS coin_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reviewed_by BIGINT NULL,
    note VARCHAR(200) NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    CONSTRAINT fk_coin_request_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_coin_request_admin FOREIGN KEY (reviewed_by) REFERENCES users(id),
    INDEX idx_coin_request_status_time (status, requested_at),
    INDEX idx_coin_request_user_time (user_id, requested_at)
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    hand_id CHAR(36) NULL,
    coin_request_id BIGINT NULL,
    admin_user_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    transaction_type VARCHAR(24) NOT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    reason VARCHAR(200) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_hand FOREIGN KEY (hand_id) REFERENCES hands(id),
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_wallet_coin_request FOREIGN KEY (coin_request_id) REFERENCES coin_requests(id),
    CONSTRAINT fk_wallet_admin FOREIGN KEY (admin_user_id) REFERENCES users(id),
    UNIQUE KEY uq_hand_user_transaction (hand_id, user_id, transaction_type),
    UNIQUE KEY uq_coin_request_transaction (coin_request_id, transaction_type)
);

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    details_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_admin_audit_admin FOREIGN KEY (admin_user_id) REFERENCES users(id),
    CONSTRAINT fk_admin_audit_target FOREIGN KEY (target_user_id) REFERENCES users(id),
    INDEX idx_admin_audit_time (created_at)
);
