// pages/login/login.js
const { post } = require('../../utils/request');

Page({
  data: {
    loading: false,
  },

  onLoad() {
    // If already logged in, go to home
    const token = wx.getStorageSync('token');
    if (token) {
      wx.switchTab({ url: '/pages/home/home' });
    }
  },

  onWechatLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });

    wx.login({
      success: (res) => {
        if (!res.code) {
          wx.showToast({ title: '获取登录码失败', icon: 'none' });
          this.setData({ loading: false });
          return;
        }
        this._doLogin(res.code);
      },
      fail: () => {
        wx.showToast({ title: '微信登录失败', icon: 'none' });
        this.setData({ loading: false });
      },
    });
  },

  onUserAgreement() {
    wx.navigateTo({ url: '/pages/agreement/agreement?type=user' });
  },

  onPrivacyPolicy() {
    wx.navigateTo({ url: '/pages/agreement/agreement?type=privacy' });
  },

  _doLogin(code) {
    post('/user/login', { code })
      .then((data) => {
        // data = { token, userId, nickname, avatar }
        wx.setStorageSync('token', data.token);
        const app = getApp();
        app.globalData.token = data.token;
        app.setUserInfo({
          userId: data.userId,
          nickname: data.nickname,
          avatar: data.avatar || '',
        });
        wx.switchTab({ url: '/pages/home/home' });
      })
      .catch((err) => {
        wx.showToast({
          title: err.message || '登录失败，请重试',
          icon: 'none',
          duration: 2000,
        });
      })
      .finally(() => {
        this.setData({ loading: false });
      });
  },
});
