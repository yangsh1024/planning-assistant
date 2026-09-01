const { get, post } = require('../../../utils/request');
/** 在已登录小程序中输入六码并确认浏览器登录请求。 */
Page({
  data: { requestId: '', deviceLabel: '', loading: true, loginCode: '' },
  /**
   * 读取可能携带的登录请求。
   * <ol><li>校验登录</li><li>解析标识</li><li>加载请求</li></ol>
   *
   * @param {object} options 页面入口参数
   */
  onLoad(options) { if (!getApp().checkAuth()) return; const requestId = options.requestId || ''; if (requestId) this.load(requestId); else this.setData({ loading: false }); },
  /**
   * 加载浏览器登录请求的预览信息。
   * <ol><li>请求预览</li><li>展示设备</li><li>处理失败</li></ol>
   *
   * @param {string} requestId 浏览器登录请求标识
   */
  load(requestId) { get(`/web-auth/requests/${requestId}/preview`).then((data) => this.setData({ requestId, deviceLabel: data.deviceLabel, loading: false })).catch((err) => { wx.showToast({ title: err.message || '登录请求无效', icon: 'none' }); this.setData({ loading: false }); }); },
  inputLoginCode(e) { this.setData({ loginCode: e.detail.value }); },
  resolveLoginCode() {
    if (!/^\d{6}$/.test(this.data.loginCode)) {
      wx.showToast({ title: '请输入六位登录码', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    post('/web-auth/login-codes/resolve', { loginCode: this.data.loginCode })
      .then((data) => this.setData({ requestId: data.requestId, deviceLabel: data.deviceLabel, loading: false }))
      .catch((err) => {
        wx.showToast({ title: err.message || '登录码无效或已失效', icon: 'none' });
        this.setData({ loading: false });
      });
  },
  /**
   * 明确同意当前浏览器登录请求。
   * <ol><li>确认请求</li><li>反馈结果</li></ol>
   */
  // 明确确认才会授权浏览器，避免扫码后自动建立 Web 会话。
  approve() { if (!this.data.requestId) return; post(`/web-auth/requests/${this.data.requestId}/approve`, {}).then(() => { wx.showToast({ title: '已确认', icon: 'success' }); setTimeout(() => wx.switchTab({ url: '/pages/home/home' }), 800); }).catch((err) => wx.showToast({ title: err.message || '确认失败', icon: 'none' })); },
  reject() { if (!this.data.requestId) { wx.switchTab({ url: '/pages/home/home' }); return; } post(`/web-auth/requests/${this.data.requestId}/reject`, {}).finally(() => wx.switchTab({ url: '/pages/home/home' })); },
});
