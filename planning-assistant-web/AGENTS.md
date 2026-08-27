# Agent Web 客户端开发约束

## 当前状态

- 阶段二 Web 客户端已使用 Vue 3、TypeScript、Vite、Tailwind CSS 和 Pinia 实现。
- 技术选型、目录结构、运行和验证命令以本目录 `ARCHITECTURE.md` 为准，不引入第二套前端框架。

## 实现约束

- 新建 UI 组件前先检查 `src/components/`。对话气泡、状态视图和会话列表应作为可复用组件实现，而不是复制到页面中。
- REST 响应遵循 `{ code, message, data }`；Web 认证使用同域 HttpOnly Cookie 与 `credentials: 'same-origin'`，非安全请求追加 `X-CSRF-TOKEN`；401 时回到登录页。
- 对话流使用 `fetch` + `ReadableStream` 处理 POST SSE，不使用仅支持 GET 的 `EventSource`。
- 使用设计 token（CSS 变量或 Tailwind 主题）管理颜色、间距和圆角；间距使用 4 或 8px 倍数，并支持明暗主题。
- 修改前端技术栈、目录结构、运行或验证方式时，同步更新 `ARCHITECTURE.md`。
