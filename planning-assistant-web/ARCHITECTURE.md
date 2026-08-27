# Agent Web 客户端架构说明（阶段二）

## 技术选型

| 领域 | 选型 |
|------|------|
| 框架 | Vue 3（Composition API）或 React 18 |
| 构建 | Vite |
| 样式 | Tailwind CSS 或 UnoCSS |
| 状态管理 | Pinia（Vue）或 Zustand（React） |
| SSE | 原生 `fetch` + `ReadableStream`（不使用 `EventSource`，不支持 POST） |

## 页面结构

```
src/
├── pages/
│   ├── Login.vue            # JWT 登录（复用 /api/user/login）
│   └── Chat.vue             # Agent 对话主界面（SSE 流式）
├── components/
│   ├── MessageBubble/       # 对话气泡（支持 Markdown 渲染）
│   ├── StateView/           # 加载中 / 空会话 / 请求失败 统一展示
│   └── SessionList/         # 历史会话列表
├── api/                     # 接口封装，统一携带 Authorization 头
└── stores/                  # 状态管理
```

> 本项目是纯 Agent 对话界面，不需要表单类组件（日期选择、金额输入、下拉框等）；预算和记账数据通过 Agent 对话操作。

## 登录

复用服务端 `POST /api/user/login`，返回相同 JWT，存本地后续请求头携带。

## SSE 流式对话

```js
const res = await fetch('/api/agent/chat', {
    method: 'POST',
    headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json'
    },
    body: JSON.stringify({ sessionId, message })
})
const reader = res.body.getReader()
// 逐块读取并追加到消息气泡
```

响应 `Content-Type: text/event-stream`，普通 REST 接口与小程序共用同一套 URL。

## 接口约定

- 统一响应体：`{ code, message, data }`
- 日期格式：`yyyy-MM`（年月）、`yyyy-MM-dd`（日期）
- 金额单位：元，保留两位小数
- 401 时跳回登录页
