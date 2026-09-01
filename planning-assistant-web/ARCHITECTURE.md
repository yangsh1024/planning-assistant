# Agent Web 客户端架构说明（阶段二）

## 技术选型

| 领域 | 选型 |
|------|------|
| 框架 | Vue 3 + TypeScript（Composition API） |
| 构建 | Vite |
| 样式 | Tailwind CSS + CSS 设计 token（跟随系统明暗主题） |
| 状态管理 | Pinia |
| SSE | 原生 `fetch` + `ReadableStream`（不使用 `EventSource`，不支持 POST） |

## 页面结构

```
src/
├── pages/
│   ├── LoginView.vue        # 小程序确认或短链交换 Web Cookie
│   └── ChatView.vue         # Agent 对话主界面（POST SSE）
├── components/
│   ├── MessageBubble.vue    # 对话气泡（Markdown 白名单净化）
│   ├── StateView.vue        # 加载中 / 空会话 / 请求失败 统一展示
│   ├── SessionList.vue      # 历史会话列表
│   └── ActionConfirm.vue    # 写操作确认与最终状态
└── api.ts                   # Cookie + CSRF 接口封装
```

> 本项目是纯 Agent 对话界面，不需要表单类组件（日期选择、金额输入、下拉框等）；预算和记账数据通过 Agent 对话操作。

## 登录与运行方式

Web 不复用小程序 `wx.login()` 或 Bearer Token。小程序确认或短链交换为 HttpOnly Web Cookie；前端使用 `credentials: 'same-origin'`，从同域 XSRF Cookie 读取 Token 并为非 GET 请求添加 `X-CSRF-TOKEN`。

本地运行前，将 `.env.development.local.example` 复制为 `.env.development.local` 并填写 `VITE_API_PROXY_TARGET`；后者已被 Git 忽略。随后执行 `npm install && npm run dev`，Vite 会将 `/api` 转发至该地址。

构建命令为 `npm run build:test`（测试）和 `npm run build:prod`（生产；`npm run build` 等价于生产模式）。两类静态包的 API 均保持相对路径 `/api`，不写入后端绝对地址。部署时将 `dist` 放入对应环境的同域 Nginx，由 Nginx 的 `/api/` 反向代理到该环境的后端；因此浏览器 Cookie、CSRF 与 SSE 均维持同源。

浏览器不能调用小程序 `wx.login()`，因此不复用 `POST /api/user/login`。电脑端展示固定小程序码和本次六位登录码；用户扫码进入小程序确认页、输入六码后确认登录。

个人主体手机端由已登录小程序复制一次性短链，用户自行粘贴到浏览器打开；不依赖 `web-view`、URL Link 或 URL Scheme，也不承诺小程序自动打开系统浏览器。一次性票据位于 URL fragment，链接不包含长期 JWT；Web 以 POST 交换 Cookie 后立即从地址栏清除 fragment。

## SSE 流式对话

```js
const res = await fetch('/api/agent/chat', {
    method: 'POST',
    headers: {
        'X-CSRF-TOKEN': csrfToken,
        'Content-Type': 'application/json'
    },
    credentials: 'same-origin',
    body: JSON.stringify({ sessionId, message, thinkingEnabled })
})
const reader = res.body.getReader()
// 逐块读取并追加到消息气泡
```

响应 `Content-Type: text/event-stream`，普通 REST 接口与小程序共用同一套 URL。

公开事件仅有 `message_start`、`delta`、`action_required`、`done`、`error`。原始 function call、工具结果和推理内容不会发送到浏览器；`action_required` 只渲染确认卡片，确认或取消通过独立 REST 请求完成。

## 接口约定

- 统一响应体：`{ code, message, data }`
- 日期格式：`yyyy-MM`（年月）、`yyyy-MM-dd`（日期）
- 金额单位：元，保留两位小数
- 401 时跳回登录页
- 深度思考开关保存在浏览器本地；只影响下一次消息，前端仅显示“正在分析”，不显示推理内容
- Markdown 经白名单净化；外链在新窗口打开并设置 `noopener noreferrer`
