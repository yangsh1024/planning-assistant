/** 根据入口展示用户协议或隐私政策，避免维护两套页面状态。 */
Page({
  data: {
    type: 'user', // 'user' | 'privacy'
    title: '',
    content: '',
  },

  onLoad(options) {
    const type = options.type || 'user';
    const isUser = type === 'user';
    this.setData({
      type,
      title: isUser ? '用户协议' : '隐私政策',
      content: isUser ? this._userAgreement() : this._privacyPolicy(),
    });
    wx.setNavigationBarTitle({ title: isUser ? '用户协议' : '隐私政策' });
  },

  _userAgreement() {
    return `欢迎使用小猫月度预算账本。

一、服务说明
本应用提供月度预算规划与开销记录功能，仅供个人使用。

二、用户责任
用户需对自己录入的数据负责，请妥善保管账号信息。

三、免责声明
本应用不对因用户操作失误导致的数据丢失承担责任。

四、协议变更
本协议可能不定期更新，继续使用即表示接受最新版本。

如有疑问，请通过应用内反馈联系我们。`;
  },

  _privacyPolicy() {
    return `小猫月度预算账本重视您的隐私保护。

一、收集的信息
我们仅收集您主动录入的预算与开销数据，以及微信登录所需的 OpenID。

二、信息使用
您的数据仅用于提供本应用功能，不会用于广告或分析目的。

三、信息存储
数据存储于安全的服务器，传输全程加密。

四、信息共享
我们不会将您的个人信息出售或共享给第三方。

五、您的权利
您可随时在个人设置中申请删除账号及所有数据。

如有疑问，请通过应用内反馈联系我们。`;
  },
});
