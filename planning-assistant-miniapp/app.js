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
   * Check if user is logged in; redirect to login if not.
   * Call from page onLoad when auth is required.
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
