const { get, post } = require('../../../utils/request');
Page({
  data: { requestId: '', deviceLabel: '', loading: true, fallbackCode: '' },
  onLoad(options) { if (!getApp().checkAuth()) return; const requestId = options.requestId || (options.scene ? decodeURIComponent(options.scene) : ''); if (requestId) this.load(requestId); else this.setData({ loading: false }); },
  load(requestId) { get(`/web-auth/requests/${requestId}/preview`).then((data) => this.setData({ requestId, deviceLabel: data.deviceLabel, loading: false })).catch((err) => { wx.showToast({ title: err.message || '登录请求无效', icon: 'none' }); this.setData({ loading: false }); }); },
  scanWebLogin() {
    wx.scanCode({
      onlyFromCamera: true,
      success: (result) => {
        const requestId = this.parseRequestId(result.path || result.result || '');
        if (!requestId) { wx.showToast({ title: '未识别到有效的网页登录码', icon: 'none' }); return; }
        this.setData({ loading: true });
        this.load(requestId);
      },
      fail: (error) => { if (!error.errMsg || !error.errMsg.includes('cancel')) wx.showToast({ title: '扫码失败，请重试', icon: 'none' }); },
    });
  },
  parseRequestId(raw) {
    let decoded = raw;
    try { decoded = decodeURIComponent(raw); } catch (error) { /* 使用原始扫码结果 */ }
    const match = decoded.match(/(?:scene|requestId)=([a-f0-9]{32})(?:&|$)/i);
    if (match) return match[1];
    return /^[a-f0-9]{32}$/i.test(decoded) ? decoded : '';
  },
  inputCode(e) { this.setData({ fallbackCode: e.detail.value }); },
  resolveCode() { if (!/^\d{6}$/.test(this.data.fallbackCode)) { wx.showToast({ title: '请输入六位登录码', icon: 'none' }); return; } post('/web-auth/fallback/resolve', { fallbackCode: this.data.fallbackCode }).then((data) => this.setData({ requestId: data.requestId, deviceLabel: data.deviceLabel })).catch((err) => wx.showToast({ title: err.message || '登录码无效', icon: 'none' })); },
  approve() { if (!this.data.requestId) return; post(`/web-auth/requests/${this.data.requestId}/approve`, {}).then(() => wx.showToast({ title: '已确认', icon: 'success' })).catch((err) => wx.showToast({ title: err.message || '确认失败', icon: 'none' })); },
  reject() { if (!this.data.requestId) { wx.navigateBack(); return; } post(`/web-auth/requests/${this.data.requestId}/reject`, {}).finally(() => wx.navigateBack()); },
});
