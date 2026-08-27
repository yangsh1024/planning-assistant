# 小猫月度预算账本 — 整体架构总览

## 项目目标

构建支持月度预算规划与日常账单记录的全栈应用，分两阶段演进：
- 阶段一：微信小程序 + Java 单体服务端
- 阶段二：新增 AI Agent 模块 + Web 客户端

## 整体架构

```
┌─────────────────────────────────────────────────┐
│                   客户端层                        │
│ 微信小程序（账本 / 授权）    Web Client（Agent）  │
└──────────────────────┬──────────────────────────┘
                       │ HTTPS
                       ▼
        Nginx（反向代理 / SSL 终止 / 基础限流）
                       │
                       ▼
┌──────────────────────────────────────────────────┐
│            单体 Spring Boot 应用                  │
│                                                  │
│  Spring Security（JWT 鉴权 / UserContext 注入）   │
│                                                  │
│  ┌─────────┐  ┌─────────┐  ┌──────────────────┐ │
│  │  user   │  │  plan   │  │     expense      │ │
│  └─────────┘  └─────────┘  └──────────────────┘ │
│                    ↑ @Autowired Service Bean      │
│           ┌────────────────────┐                 │
│           │   agent（阶段二）   │                 │
│           │ LLM 网关 + Tool 策略│                 │
│           └────────────────────┘                 │
└──────────────────────┬───────────────────────────┘
                       │
                     MySQL
```

## 关键决策

- 不单独部署 Spring Cloud Gateway，由 Nginx + Spring Security 组合替代，节省单机资源
- Agent 合并进单体应用，通过 Spring Bean 注入调用业务 Service，不直连数据库；账本写操作必须经确认动作执行
- 权限由 Spring SecurityContext 统一控制，小程序 Bearer 与 Web Cookie 使用不同 token purpose 和角色
- 个人主体小程序不使用 `web-view`、URL Link 或 URL Scheme；电脑端使用小程序码确认登录，手机端通过小程序复制短时一次性登录链接
- **业务规则、数据模型及接口定义以各子模块 ARCHITECTURE.md 为准**，本文件仅作整体概览

## 子模块文档

- 服务端：[planning-assistant-server/ARCHITECTURE.md](planning-assistant-server/ARCHITECTURE.md)
- 微信小程序：[planning-assistant-miniapp/ARCHITECTURE.md](planning-assistant-miniapp/ARCHITECTURE.md)
- Agent Web 客户端：[planning-assistant-web/ARCHITECTURE.md](planning-assistant-web/ARCHITECTURE.md)

## 技术选型

| 领域 | 选型 |
|------|------|
| Java | JDK 21 |
| 框架 | Spring Boot 3.3 |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8.x |
| 鉴权 | Spring Security + JWT（jjwt） |
| 限流 | Nginx 基础限流 |
| 入口 | Nginx（SSL 终止 + 反向代理） |
| 构建 | Maven 多模块，单 fat JAR |
| 小程序 | 原生微信小程序 |
| Web 客户端 | Vue 3 + TypeScript + Vite + Pinia + Tailwind CSS |
| Agent | DeepSeek Responses API（默认 `deepseek-v4-flash`） |

## 阶段规划

**阶段一：** Maven 骨架 → common/security → 微信登录 → plan/expense 模块 → 小程序联调

**阶段二：** Web Cookie/CSRF → DeepSeek function tools → Web POST SSE → 写操作确认 → 小程序扫码/短链登录
