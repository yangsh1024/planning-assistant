/** 展示月度预算执行摘要，并标识超支分类供用户继续查看。 */
const { get } = require('../../utils/request');
const { formatYearMonth } = require('../../utils/date');

Page({
  data: {
    currentYearMonth: '',
    maxYearMonth: '',
    greeting: '你好',
    nickname: '朋友',
    summary: null,
    pageStatus: 'loading', // 'loading' | 'no-plan' | 'empty' | 'error' | 'done'
    pageMessage: '',
    refreshing: false,
  },

  onLoad() {
    if (!getApp().checkAuth()) return;
    const now = new Date();
    const ym = formatYearMonth(now.getFullYear(), now.getMonth() + 1);
    this.setData({
      currentYearMonth: ym,
      maxYearMonth: ym,
      greeting: this._getGreeting(now),
    });
    this._loadProfile();
    this.loadData();
  },

  onShow() {
    if (this.data.currentYearMonth) {
      this._loadProfile();
      this.loadData();
    }
  },

  loadData() {
    // 摘要缺少预算时保留单独状态，引导用户建立月度计划而非显示空白。
    this.setData({ pageStatus: 'loading', pageMessage: '' });
    get(`/plan/${this.data.currentYearMonth}/summary`)
      .then((data) => {
        const hasPlan = data && data.totalBudget !== '0.00';
        const summary = Object.assign({}, data, {
          isOverBudget: this._isNegativeAmount(data && data.totalRemaining),
          items: (data && data.items ? data.items : []).map((item) => {
            const isOverBudget = this._isNegativeAmount(item.remaining);
            return Object.assign({}, item, {
              isOverBudget,
              overAmount: isOverBudget ? this._absoluteAmount(item.remaining) : '',
            });
          }),
        });
        this.setData({ summary, pageStatus: hasPlan ? 'done' : 'no-plan' });
      })
      .catch((err) => {
        this.setData({
          pageStatus: 'error',
          pageMessage: err.message || '加载失败，请重试',
        });
      })
      .finally(() => {
        this.setData({ refreshing: false });
      });
  },

  onRefresh() {
    this.setData({ refreshing: true });
    this.loadData();
  },

  onYearMonthChange(e) {
    const yearMonth = e.detail.value;
    if (!yearMonth || yearMonth === this.data.currentYearMonth) return;
    this.setData({
      currentYearMonth: yearMonth,
      summary: null,
    });
    this.loadData();
  },

  onViewDetails() {
    getApp().globalData.pendingStatsNavigation = {
      yearMonth: this.data.currentYearMonth,
      tab: 'list',
    };
    wx.switchTab({
      url: '/pages/expense/stats/stats',
      fail: () => wx.showToast({ title: '打开明细失败，请重试', icon: 'none' }),
    });
  },

  onAddExpense() {
    get('/plan/has-any')
      .then((hasAnyPlan) => {
        if (hasAnyPlan) {
          wx.navigateTo({ url: `/pages/expense/add/add?yearMonth=${this.data.currentYearMonth}` });
          return;
        }
        wx.navigateTo({
          url: `/pages/plan/edit/edit?yearMonth=${this.data.currentYearMonth}&requirePlan=1&returnTo=expense`,
        });
      })
      .catch((err) => wx.showToast({ title: err.message || '检查预算失败', icon: 'none' }));
  },

  onEditPlan() {
    wx.navigateTo({ url: `/pages/plan/edit/edit?yearMonth=${this.data.currentYearMonth}` });
  },

  _loadProfile() {
    const cached = getApp().globalData.userInfo;
    if (cached && cached.nickname) this.setData({ nickname: cached.nickname });
    get('/user/profile').then((profile) => {
      getApp().setUserInfo(profile);
      this.setData({ nickname: profile.nickname || '朋友' });
    }).catch(() => {});
  },

  _getGreeting(now) {
    if (now.getHours() < 12) return '早上好';
    if (now.getHours() < 18) return '下午好';
    return '晚上好';
  },

  _isNegativeAmount(amount) {
    return /^-/.test(String(amount || ''));
  },

  _absoluteAmount(amount) {
    return String(amount || '0.00').replace(/^-/, '');
  },

});
