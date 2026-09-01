-- 阶段二 Agent Web 生产增量迁移。执行前请备份数据库。
-- 适用于已完成阶段一建表的 MySQL 8 实例；仅新增表，不修改既有账本数据。

CREATE TABLE IF NOT EXISTS t_web_login_request (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NULL,
    browser_proof_hash CHAR(64) NOT NULL,
    fallback_code_hash CHAR(64) NULL COMMENT '六位登录码 SHA-256 摘要（列名为历史兼容）',
    device_label VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_web_fallback_code_hash (fallback_code_hash),
    INDEX idx_web_login_expiry (expires_at)
) COMMENT '网页登录请求';

CREATE TABLE IF NOT EXISTS t_web_sso_ticket (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    ticket_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    consumed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_web_ticket_hash (ticket_hash),
    INDEX idx_web_ticket_expiry (expires_at)
) COMMENT '网页短链接票据';

CREATE TABLE IF NOT EXISTS t_agent_session (
    id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_agent_session_user_updated (user_id, updated_at)
) COMMENT 'Agent 会话';

CREATE TABLE IF NOT EXISTS t_agent_message (
    id CHAR(36) PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    action_id CHAR(36) NULL,
    model VARCHAR(64) NULL,
    input_tokens INT NULL,
    output_tokens INT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_agent_message_session_created (session_id, created_at)
) COMMENT 'Agent 可见消息';

CREATE TABLE IF NOT EXISTS t_agent_action_audit (
    id CHAR(36) PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    payload_json JSON NOT NULL,
    target_fingerprint CHAR(64) NULL,
    idempotency_key CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    expires_at DATETIME NOT NULL,
    result_json JSON NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_agent_action_idempotency (idempotency_key),
    INDEX idx_agent_action_user_status (user_id, status, expires_at)
) COMMENT 'Agent 写操作审计';
