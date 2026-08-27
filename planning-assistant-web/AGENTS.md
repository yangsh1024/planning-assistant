# Agent Web 客户端开发约束

## 当前状态

- 此目录目前只有阶段二架构设计，没有可运行的 Web 项目或构建配置。
- 实现前先确认选用 Vue 3 或 React 18、构建工具和样式方案；不要在未确认时同时引入两套框架。

## 实现约束

- 新建 UI 组件前先检查 `src/components/`。对话气泡、状态视图和会话列表应作为可复用组件实现，而不是复制到页面中。
- REST 响应遵循 `{ code, message, data }`；认证请求统一携带 `Authorization: Bearer <token>`；401 时回到登录页。
- 对话流使用 `fetch` + `ReadableStream` 处理 POST SSE，不使用仅支持 GET 的 `EventSource`。
- 使用设计 token（CSS 变量或 Tailwind 主题）管理颜色、间距和圆角；间距使用 4 或 8px 倍数，并支持明暗主题。
- 开始实施前，将选定的前端技术栈、目录结构、运行和验证命令补充到 `ARCHITECTURE.md`。
