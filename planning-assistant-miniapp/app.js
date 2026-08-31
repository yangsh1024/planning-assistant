/** 管理小程序启动时的本地登录恢复，并为需要身份的页面提供统一拦截。 */
App({
  globalData: {
    userInfo: null,
    token: null,
    pendingStatsNavigation: null,
  },

  onLaunch() {
    const token = wx.getStorageSync('token');
    const userInfo = wx.getStorageSync('userInfo');
    if (token) {
      this.globalData.token = token;
    }
    if (userInfo) {
      this.globalData.userInfo = userInfo;
    }
  },

  /**
   * 确认页面访问者已登录。
   * <ol><li>读取令牌</li><li>同步状态</li><li>跳转登录</li></ol>
   * @return 是否存在可用的本地登录令牌
   */
  checkAuth() {
    const token = wx.getStorageSync('token');
    if (!token) {
      wx.reLaunch({ url: '/pages/login/login' });
      return false;
    }
    this.globalData.token = token;
    return true;
  },

  setUserInfo(userInfo) {
    this.globalData.userInfo = userInfo;
    wx.setStorageSync('userInfo', userInfo);
  },

});
