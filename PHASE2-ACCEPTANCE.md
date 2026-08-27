# 阶段二 Agent Web 回归与验收状态

更新时间：2026-08-28

## 已完成的代码与自动化检查

| 阶段 | 已完成范围 | 自动化证据 |
|---|---|---|
| 一：身份体系 | 动态码/固定码契约、六位码解析、短链、摘要存储、单次交换、Web Cookie、选择性 CSRF、小程序/Web 角色隔离 | WebAuth、JWT、认证来源、CSRF 单元测试 |
| 二：DeepSeek 只读 Agent | Responses 流解析、`none/low` 思考参数、四类只读工具、内部推理项回传、非法工具参数纠正、总超时、429/断流、输出上限 | DeepSeek 网关、思考策略、Tool schema/执行器单元测试 |
| 三：Web 客户端 | Vue 3/TS/Vite/Tailwind/Pinia、登录、POST SSE、会话恢复、Markdown 净化、深度思考本地偏好 | Vitest SSE 分片测试、`npm run build` |
| 四：写确认 | 五类待确认动作、参数预校验、可读摘要、归属/过期/幂等/并发 claim/目标指纹、`STALE`、失败结果恢复 | 动作策略、服务和工具执行器单元测试 |
| 五：小程序与部署物 | Profile 复制短链、确认/拒绝、扫码与六位码兜底、迁移 SQL、Nginx SSE 配置、环境变量样例、API 冒烟脚本 | 小程序静态检查、脚本语法检查、OpenAPI YAML 解析 |

## 发布前仍需完成

以下项目依赖真实微信、DeepSeek、域名或生产服务器，不在本地自动化结果中标记为通过：

1. 使用当前个人主体 AppID 真机验证动态小程序码；未通过前保持 `WECHAT_DYNAMIC_QR_ENABLED=false`。
2. 真机验证固定小程序码、`wx.scanCode()`、六位码确认/拒绝/过期流程，以及 Profile 复制短链流程。
3. 在隔离测试账本中配置有效 DeepSeek Key，执行普通/深度思考、四类只读工具和五类写确认的真实端到端验收。
4. 替换 Nginx 模板中的域名、证书和 Web 构建目录后执行 `nginx -t`，再验证生产代理不缓冲 SSE、断连释放资源。
5. 对已部署环境运行 `planning-assistant-server/scripts/verify-agent-web-e2e.sh`；`RUN_MODEL_CHECKS=true` 验证普通/思考流，`RUN_WRITE_ACTION_CHECKS=true` 验证动作生成与取消，隔离账本中再启用 `E2E_ALLOW_LEDGER_WRITES=true` 验证确认及重复确认；`RUN_EXPIRY_CHECK=true` 和 `RUN_DISCONNECT_CHECK=true` 分别覆盖真实计时过期与客户端断连路径。

## 当前非阻断增强项

- 动态码在微信接口运行时失败时，当前由运维关闭动态码开关回退，尚未在同一个浏览器请求中自动切换模式。
- 公共登录请求的基础限流依赖 Nginx 模板中的 `limit_req` 配置；上线时需按实际流量启用。
