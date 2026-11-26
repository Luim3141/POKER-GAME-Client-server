USE poker_game;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS account_role VARCHAR(16) NOT NULL DEFAULT 'PLAYER' AFTER password_iterations,
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' AFTER account_role;

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

ALTER TABLE wallet_transactions
    MODIFY hand_id CHAR(36) NULL,
    ADD COLUMN IF NOT EXISTS coin_request_id BIGINT NULL AFTER hand_id,
    ADD COLUMN IF NOT EXISTS admin_user_id BIGINT NULL AFTER coin_request_id,
    ADD COLUMN IF NOT EXISTS reason VARCHAR(200) NULL AFTER balance_after;

SET @fk_coin := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='wallet_transactions' AND CONSTRAINT_NAME='fk_wallet_coin_request');
SET @sql := IF(@fk_coin=0, 'ALTER TABLE wallet_transactions ADD CONSTRAINT fk_wallet_coin_request FOREIGN KEY (coin_request_id) REFERENCES coin_requests(id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @fk_admin := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='wallet_transactions' AND CONSTRAINT_NAME='fk_wallet_admin');
SET @sql := IF(@fk_admin=0, 'ALTER TABLE wallet_transactions ADD CONSTRAINT fk_wallet_admin FOREIGN KEY (admin_user_id) REFERENCES users(id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @uq_request := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='wallet_transactions' AND INDEX_NAME='uq_coin_request_transaction');
SET @sql := IF(@uq_request=0, 'ALTER TABLE wallet_transactions ADD UNIQUE KEY uq_coin_request_transaction (coin_request_id,transaction_type)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
