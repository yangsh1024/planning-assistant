-- 开发库完整初始化脚本（破坏性操作）。
-- 请先在数据库客户端中选择目标库 planning_assistant，再执行本文件。
-- 会删除 t_user、t_category、t_budget_plan、t_budget_item、t_expense 的所有数据与结构。

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS t_expense;
DROP TABLE IF EXISTS t_agent_action_audit;
DROP TABLE IF EXISTS t_agent_message;
DROP TABLE IF EXISTS t_agent_session;
DROP TABLE IF EXISTS t_web_sso_ticket;
DROP TABLE IF EXISTS t_web_login_request;
DROP TABLE IF EXISTS t_budget_item;
DROP TABLE IF EXISTS t_budget_plan;
DROP TABLE IF EXISTS t_category;
DROP TABLE IF EXISTS t_user;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE t_user
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid     VARCHAR(64)  NOT NULL UNIQUE COMMENT '微信 openid',
    nickname   VARCHAR(50)  NULL COMMENT '昵称',
    avatar     VARCHAR(32)  NOT NULL DEFAULT 'cat-orange' COMMENT '内置小猫头像 key',
    phone      VARCHAR(20)  NULL COMMENT '手机号',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间'
) COMMENT '用户表';

CREATE TABLE t_category
(
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID，0=系统预置',
    name       VARCHAR(20)  NOT NULL COMMENT '科目名称',
    is_system  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否系统预置',
    is_deleted TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_user_name (user_id, name)
) COMMENT '开销科目表';

INSERT INTO t_category (user_id, name, is_system, is_deleted)
VALUES (0, '饮食', 1, 0),
       (0, '交通', 1, 0),
       (0, '租房', 1, 0),
       (0, '通信', 1, 0),
       (0, '其他', 1, 0);

CREATE TABLE t_budget_plan
(
    id           BIGINT     AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT     NOT NULL COMMENT '用户ID',
    `year_month` VARCHAR(7) NOT NULL COMMENT '年月，格式 yyyy-MM',
    created_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_month (user_id, `year_month`)
) COMMENT '月度预算主表';

CREATE TABLE t_budget_item
(
    id          BIGINT         AUTO_INCREMENT PRIMARY KEY,
    plan_id     BIGINT         NOT NULL COMMENT '预算计划ID',
    category_id BIGINT         NOT NULL COMMENT '科目ID',
    amount      DECIMAL(12, 2) NOT NULL COMMENT '预算金额',
    sort_order  INT            NOT NULL DEFAULT 0 COMMENT '科目排序，按月独立维护',
    INDEX idx_plan_id (plan_id)
) COMMENT '预算明细表';

CREATE TABLE t_expense
(
    id           BIGINT         AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT         NOT NULL COMMENT '用户ID',
    category_id  BIGINT         NOT NULL COMMENT '科目ID',
    amount       DECIMAL(12, 2) NOT NULL COMMENT '金额',
    expense_date DATE           NOT NULL COMMENT '消费日期',
    note         VARCHAR(100)   NULL COMMENT '备注',
    is_deleted   TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否软删除',
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_date (user_id, expense_date)
) COMMENT '开销流水表';

CREATE TABLE t_web_login_request (
    id VARCHAR(36) PRIMARY KEY, user_id BIGINT NULL, browser_proof_hash CHAR(64) NOT NULL,
    fallback_code_hash CHAR(64) NULL COMMENT '六位登录码 SHA-256 摘要（列名为历史兼容）', device_label VARCHAR(80) NOT NULL, status VARCHAR(16) NOT NULL,
    expires_at DATETIME NOT NULL, created_at DATETIME NOT NULL,
    UNIQUE KEY uk_web_fallback_code_hash (fallback_code_hash), INDEX idx_web_login_expiry (expires_at)
) COMMENT '网页登录请求';
CREATE TABLE t_web_sso_ticket (
    id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, ticket_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL, consumed_at DATETIME NULL, created_at DATETIME NOT NULL,
    UNIQUE KEY uk_web_ticket_hash (ticket_hash), INDEX idx_web_ticket_expiry (expires_at)
) COMMENT '网页短链接票据';
CREATE TABLE t_agent_session (
    id CHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, title VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, INDEX idx_agent_session_user_updated (user_id, updated_at)
) COMMENT 'Agent 会话';
CREATE TABLE t_agent_message (
    id CHAR(36) PRIMARY KEY, session_id CHAR(36) NOT NULL, user_id BIGINT NOT NULL,
    role VARCHAR(16) NOT NULL, content TEXT NOT NULL, action_id CHAR(36) NULL, model VARCHAR(64) NULL,
    input_tokens INT NULL, output_tokens INT NULL, status VARCHAR(16) NOT NULL, created_at DATETIME NOT NULL,
    INDEX idx_agent_message_session_created (session_id, created_at)
) COMMENT 'Agent 可见消息';
CREATE TABLE t_agent_action_audit (
    id CHAR(36) PRIMARY KEY, session_id CHAR(36) NOT NULL, user_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL, summary VARCHAR(500) NOT NULL, payload_json JSON NOT NULL,
    target_fingerprint CHAR(64) NULL, idempotency_key CHAR(64) NOT NULL, status VARCHAR(24) NOT NULL,
    expires_at DATETIME NOT NULL, result_json JSON NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_agent_action_idempotency (idempotency_key), INDEX idx_agent_action_user_status (user_id, status, expires_at)
) COMMENT 'Agent 写操作审计';
