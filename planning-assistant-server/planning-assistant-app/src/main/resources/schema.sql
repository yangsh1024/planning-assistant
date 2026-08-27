CREATE TABLE IF NOT EXISTS t_user
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid     VARCHAR(64)  NOT NULL UNIQUE COMMENT '微信 openid',
    nickname   VARCHAR(50)  NULL COMMENT '昵称',
    avatar     VARCHAR(32)  NOT NULL DEFAULT 'cat-orange' COMMENT '内置小猫头像 key',
    phone      VARCHAR(20)  NULL COMMENT '手机号',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间'
) COMMENT '用户表';

CREATE TABLE IF NOT EXISTS t_category
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL DEFAULT 0 COMMENT '用户ID，0=系统预置',
    name       VARCHAR(20)  NOT NULL COMMENT '科目名称',
    is_system  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否系统预置',
    is_deleted TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否软删除',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_user_name (user_id, name)
) COMMENT '开销科目表';

INSERT IGNORE INTO t_category (user_id, name, is_system, is_deleted)
VALUES (0, '饮食', 1, 0),
       (0, '交通', 1, 0),
       (0, '租房', 1, 0),
       (0, '通信', 1, 0),
       (0, '其他', 1, 0);

CREATE TABLE IF NOT EXISTS t_budget_plan
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL COMMENT '用户ID',
    `year_month` VARCHAR(7)  NOT NULL COMMENT '年月，格式 yyyy-MM',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_month (user_id, `year_month`)
) COMMENT '月度预算主表';

CREATE TABLE IF NOT EXISTS t_budget_item
(
    id          BIGINT         AUTO_INCREMENT PRIMARY KEY,
    plan_id     BIGINT         NOT NULL COMMENT '预算计划ID',
    category_id BIGINT         NOT NULL COMMENT '科目ID',
    amount      DECIMAL(12, 2) NOT NULL COMMENT '预算金额',
    sort_order  INT            NOT NULL DEFAULT 0 COMMENT '科目排序，按月独立维护',
    INDEX idx_plan_id (plan_id)
) COMMENT '预算明细表';

CREATE TABLE IF NOT EXISTS t_expense
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
