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

Web 不复用小程序 `wx.login()` 或 Bearer Token。小程序确认或短链交换为 HttpOnly Web Cookie；前端使用 `credentials: 'same-origin'`，从同域 XSRF Cookie 读取 Token 并为非 GET 请求添加 `X-CSRF-TOKEN`。运行：`npm install && npm run dev`；验证：`npm run build`。

电脑端优先展示动态小程序码；未完成真机验证或能力不可用时展示运维配置的固定小程序码与六位登录码。手机端由小程序复制一次性短链，票据位于 URL fragment，交换成功后立即从地址栏清除。

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
