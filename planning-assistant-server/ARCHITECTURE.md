# 服务端架构说明

## 技术选型

| 领域 | 选型 |
|------|------|
| Java | JDK 21 |
| 框架 | Spring Boot 3.3 |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8.x |
| 鉴权 | Spring Security + JWT（jjwt） |

## Maven 模块结构

多模块组织，最终只打一个可执行 fat JAR：

```
planning-assistant/                    ← parent pom (packaging=pom)
├── pom.xml
├── planning-assistant-common/         ← 纯 Java 库，无 Spring Boot 插件
├── planning-assistant-core/           ← 业务领域代码，无 Spring Boot 插件
└── planning-assistant-app/            ← 唯一 Spring Boot 入口
    ├── pom.xml  ← 依赖 common + core，挂 spring-boot-maven-plugin
    └── src/main/java/.../Application.java
```

打包命令：

```bash
mvn clean package -pl planning-assistant-app -am
```

产出 `planning-assistant-app/target/planning-assistant-app-<version>.jar`，直接 `java -jar` 启动。

开发环境可在 `planning-assistant-server/` 目录执行：

```bash
./start-server.zsh
```

脚本会显式加载 `~/.my-source/planning-env.sh`，因为非交互式脚本不会自动读取 `~/.zshrc`；可用 `PLANNING_ENV_FILE` 覆盖该路径。执行 `./start-server.zsh --check` 可检查环境变量文件、必要的微信/JWT 配置、JDK 21 和 Maven 是否可用。脚本会在打包前检查 8080 端口，已有服务运行时会退出，避免覆盖运行中的 JAR。

微信登录始终使用正式 `code2session`，启动环境必须提供 `WECHAT_APPID`、`WECHAT_SECRET` 和 Base64 编码的 `JWT_SECRET`；三者不写入仓库，也不提供模拟登录回退。真机、体验版和正式环境的小程序 API 地址必须使用微信后台已登记的 HTTPS 域名。

### 服务器部署脚本

部署 JAR 后，将 `scripts/server.env.example` 复制到 `${APP_HOME}/conf/server.env`，填写数据库、微信、JWT、对外域名和存储路径后执行 `chmod 600`。该文件不进入仓库。

```bash
./scripts/start-server.sh
./scripts/stop-server.sh
./scripts/stop-server.sh --force  # 仅在优雅停止超时后使用
```

启动脚本校验 JDK 21、必需配置、JAR 和 PID 状态；日志写入 `${LOG_DIR}/planning-assistant.out`，PID 写入 `${RUN_DIR}/planning-assistant.pid`。默认 JVM 参数为 `-Xms256m -Xmx512m`、G1 GC、OOM 退出、UTF-8 和上海时区，可通过 `JAVA_OPTS` 覆盖。

Nginx 使用 `scripts/nginx-planning-assistant.conf.example` 作为 HTTPS 反向代理模板。替换域名和证书路径，确保小程序 `API_BASE_URL` 与 Nginx `server_name` 使用同一 HTTPS 域名；随后执行 `nginx -t` 并 reload。

## 领域包结构（core 模块内部）

**约定：** agent 包只允许 import `*.service.*`，禁止直接访问 `*.repository.*`

```
com.ysh.planning
├── common/
│   ├── response/       # Result<T>, PageResult<T>
│   ├── exception/      # BizException, GlobalExceptionHandler, 错误码枚举
│   └── security/       # JWT 工具, UserContext, SecurityConfig
├── user/
│   ├── controller/
│   ├── service/        # ← 对外边界
│   ├── repository/
│   └── domain/
├── plan/
│   ├── controller/
│   ├── service/        # ← Agent 可注入
│   ├── repository/
│   └── domain/
├── expense/
│   ├── controller/
│   ├── service/        # ← Agent 可注入
│   ├── repository/
│   └── domain/
└── agent/              # 阶段二
    ├── controller/     # SSE 接口
    ├── service/        # 对话管理、LLM 调用
    ├── tool/           # Tool 定义，注入 plan/expense service
    └── domain/         # Session, Message
```

## 权限模型

Service 层永远从 SecurityContext 读取当前用户，不信任调用方传入的 userId：

```java
public ExpenseRecord create(CreateExpenseRequest req) {
    Long userId = (Long) SecurityContextHolder.getContext()
        .getAuthentication().getPrincipal();
    // ...
}
```

Agent Tool 只传业务参数，userId 由框架保证：

```java
@Tool("create_expense")
public String createExpense(String category, BigDecimal amount, String note) {
    return expenseService.create(new CreateExpenseRequest(category, amount, note));
}
```

## 各模块接口

### user — 用户模块

| 接口 | 说明 |
|------|------|
| `POST /api/user/login` | 微信登录：code2session 换取 openid，不存在则自动注册，返回 JWT（有效期 7 天） |
| `GET /api/user/profile` | 当前用户信息（昵称、头像、注册时间） |
| `PUT /api/user/profile` | 更新昵称和内置头像 key（9 种应用内置小猫头像，完整枚举见 `api-contract.yaml`） |

主表：`t_user`（id, openid, nickname, avatar, phone, created_at）；`avatar` 保存内置头像 key，服务端仅接受约定枚举值，不处理头像文件或外部头像 URL。

### plan — 月度预算规划

| 接口 | 说明 |
|------|------|
| `POST /api/plan/{yearMonth}` | 创建或覆盖月度预算（覆盖语义，事务保证） |
| `GET /api/plan/{yearMonth}` | 查询月度预算详情；无预算返回 null |
| `GET /api/plan/{yearMonth}/summary` | 预算 vs 实际开销汇总（含总执行率；无预算时预算为 0，仍返回实际开销） |
| `GET /api/plan/has-any` | 检查当前用户是否曾设置过任何月份的预算（用于入口拦截） |
| `GET /api/categories` | 科目列表（系统预置 + 用户自定义，按用户排序） |
| `POST /api/categories` | 新建自定义科目；名称已存在则直接返回已有科目，不重复创建 |

**业务规则：**
- 年月上限为当月，不支持提前设置未来月份预算
- 保存时 items 为空则拒绝（至少一个科目），金额必须 > 0
- 科目顺序由 `t_budget_item.sort_order` 字段维护，按月独立存储

主表：`t_budget_plan`、`t_budget_item`、`t_category`

### expense — 日常开销记录

| 接口 | 说明 |
|------|------|
| `POST /api/expense` | 新增开销；`expenseDate` 不允许晚于今天 |
| `PUT /api/expense/{id}` | 编辑（科目、金额、日期、备注均可修改） |
| `DELETE /api/expense/{id}` | 软删除 |
| `GET /api/expense` | 分页列表（`yearMonth` + 可选 `categoryId`，按 `expenseDate` 倒序） |
| `GET /api/expense/stats/{yearMonth}` | 月度按科目汇总（总额、笔数、占比），含日均支出和总笔数 |
| `GET /api/expense/trend` | 近 N 月趋势 |

**月份归属规则：** 所有月份统计均以 `expense_date` 所在月为准，与记录创建时间无关。

主表：`t_expense`（id, user_id, category_id, amount, expense_date, note, is_deleted, created_at）

> 金额字段统一 `DECIMAL(12,2)`，禁止 `FLOAT/DOUBLE`

### agent — AI Agent（阶段二）

| 接口 | 说明 |
|------|------|
| `POST /api/agent/chat` | 发送消息，SSE 流式返回 |
| `GET /api/agent/sessions` | 历史会话列表 |
| `GET /api/agent/sessions/{id}/messages` | 会话消息历史 |

主表：`t_agent_session`、`t_agent_message`

## 数据库核心表设计说明

数据库不在应用启动时自动建表。开发库需要重新初始化时，先确认已连接目标库，再手动执行 `scripts/init-dev-database.sql`；该脚本会删除并重建全部业务表。已有库仅需补齐系统分类时，执行可重复运行且不删除数据的 `scripts/seed-system-categories.sql`。`schema.sql` 保留为不删除现有表的建表参考。

> - `t_category` 使用 `is_deleted` 标记已删除的自定义科目；重名科目直接复用已有记录，不新建
> - 初始化脚本会创建系统预置科目：饮食、交通、租房、通信、其他（`user_id = 0`、`is_system = 1`）
> - `t_budget_item.sort_order` 维护用户拖拽后的科目顺序，按月独立存储
> - 所有月份统计均 WHERE `expense_date` BETWEEN 月初 AND 月末，与 `created_at` 无关

```sql
CREATE TABLE IF NOT EXISTS t_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid     VARCHAR(64)  NOT NULL UNIQUE COMMENT '微信 openid',
    nickname   VARCHAR(50)  NULL,
    avatar     VARCHAR(32)  NOT NULL DEFAULT 'cat-orange' COMMENT '内置小猫头像 key',
    phone      VARCHAR(20)  NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS t_category (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL DEFAULT 0 COMMENT '0=系统预置',
    name       VARCHAR(20) NOT NULL,
    is_system  TINYINT(1)  NOT NULL DEFAULT 0,
    is_deleted TINYINT(1)  NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_user_name (user_id, name)
);

INSERT IGNORE INTO t_category (user_id, name, is_system, is_deleted)
VALUES (0, '饮食', 1, 0), (0, '交通', 1, 0), (0, '租房', 1, 0),
       (0, '通信', 1, 0), (0, '其他', 1, 0);

CREATE TABLE IF NOT EXISTS t_budget_plan (
    id           BIGINT     AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT     NOT NULL,
    `year_month` VARCHAR(7) NOT NULL COMMENT 'yyyy-MM',
    created_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_month (user_id, `year_month`)
);

CREATE TABLE IF NOT EXISTS t_budget_item (
    id          BIGINT         AUTO_INCREMENT PRIMARY KEY,
    plan_id     BIGINT         NOT NULL,
    category_id BIGINT         NOT NULL,
    amount      DECIMAL(12, 2) NOT NULL,
    sort_order  INT            NOT NULL DEFAULT 0 COMMENT '科目排序，按月独立',
    INDEX idx_plan_id (plan_id)
);

CREATE TABLE IF NOT EXISTS t_expense (
    id           BIGINT         AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT         NOT NULL,
    category_id  BIGINT         NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL,
    expense_date DATE           NOT NULL COMMENT '记账日期，月份归属以此为准',
    note         VARCHAR(100)   NULL,
    is_deleted   TINYINT(1)     NOT NULL DEFAULT 0,
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, expense_date)
);
```

阶段二额外表：`t_agent_session`、`t_agent_message`（结构待定）

> - `t_category` 使用 `is_deleted` 标记已删除的自定义科目，重名直接复用已有科目，不新建
> - `t_budget_item` 新增 `sort_order`，维护用户拖拽后的科目顺序，按月独立
> - 所有月份统计均 WHERE `expense_date` BETWEEN 月初 AND 月末，与 `created_at` 无关

## 验证方式

1. `mvn clean package -pl planning-assistant-app -am` 打包无错误
2. `java -jar` 启动，接口请求可正常处理
3. Postman：微信登录 → 设置月度预算 → 记录开销 → 查看预算汇总 → 验证 `has-any` 接口返回值
