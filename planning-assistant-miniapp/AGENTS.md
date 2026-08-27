# 微信小程序开发约束

## 组件与请求

- 新建 UI 前先检查 `components/`：日期选择使用 `components/date-picker/`，金额输入使用 `components/amount-input/`，选择器使用 `components/selector/`，页面状态使用 `components/state-view/`。
- 已有组件可满足需求时直接复用；需要扩展时优先增加组件能力，避免页面内复制同类逻辑。
- 所有 HTTP 请求必须通过 `utils/request.js`，由它统一携带 `Authorization: Bearer <token>`、处理统一响应体和 401 跳转。页面不得自行拼装请求头。
- 本项目不保留本地 Mock；修改真实接口时同步更新小程序调用与 `api-contract.yaml`，避免前后端漂移。

## 数据与交互

- 金额以字符串或整数分处理，禁止直接用 `toFixed(2)` 修正浮点误差。
- 年月使用 `yyyy-MM`，日期使用 `yyyy-MM-dd`；所有月份筛选按 `expenseDate` 归属。
- 需要登录的页面在加载时调用 `getApp().checkAuth()`。
- 遵循 PRD 的加载、空数据、失败和重试状态；写操作失败时保持用户填写的数据。

## 样式与验证

- 使用 `rpx`，以 750rpx 设计基准；间距使用 8rpx 倍数。
- 颜色、字体和间距优先使用 `app.wxss` 已定义的 CSS 变量及通用样式，不在页面或组件中随意新增硬编码色值。
- 变更小程序 UI 或交互后，使用微信开发者工具完成相关页面路径的手动验证，并在交付中说明验证结果。
