// pages/expense/add/add.js
const { get, post, put, del } = require('../../../utils/request');

Page({
  data: {
    expenseId: null,
    amountRaw: '',
    categoryId: null,
    categoryOptions: [],
    expenseDate: '',
    note: '',
    saving: false,
    currentYearMonth: '',
    today: '',
    customCategoryName: '',
    fieldErrors: {},
  },

  onLoad(options) {
    if (!getApp().checkAuth()) return;

    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    const ym = options.yearMonth || `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;

    this.setData({ expenseDate: today, currentYearMonth: ym, today });

    if (options.expenseId) {
      // Editing — no plan check needed
      this._loadCategories(() => {
        this.setData({
          expenseId: options.expenseId,
          amountRaw: options.amount || '',
          categoryId: options.categoryId ? Number(options.categoryId) : null,
          expenseDate: options.expenseDate || today,
          note: options.note ? decodeURIComponent(options.note) : '',
        });
        wx.setNavigationBarTitle({ title: '编辑记录' });
      });
    } else {
      wx.setNavigationBarTitle({ title: '新增开支' });
      this._loadCategories(() => {});
    }
  },

  _loadCategories(cb) {
    Promise.all([
      get('/categories'),
      get(`/plan/${this.data.currentYearMonth}`),
    ]).then(([data, plan]) => {
      const planOrder = new Map((plan && plan.items ? plan.items : []).map((item, index) => [
        String(item.categoryId), index,
      ]));
      const options = (data || []).map(c => ({
        value: c.categoryId,
        label: c.name,
      })).sort((a, b) => {
        const aOrder = planOrder.has(String(a.value)) ? planOrder.get(String(a.value)) : Number.MAX_SAFE_INTEGER;
        const bOrder = planOrder.has(String(b.value)) ? planOrder.get(String(b.value)) : Number.MAX_SAFE_INTEGER;
        return aOrder - bOrder;
      });
      this.setData({ categoryOptions: options });
      if (cb) cb();
    }).catch(() => {
      wx.showToast({ title: '科目加载失败', icon: 'none' });
    });
  },

  onAmountChange(e) {
    this.setData({ amountRaw: e.detail.value, fieldErrors: Object.assign({}, this.data.fieldErrors, { amount: '' }) });
  },

  onCategoryChange(e) {
    this.setData({ categoryId: e.detail.value, fieldErrors: Object.assign({}, this.data.fieldErrors, { category: '' }) });
  },

  onDateChange(e) {
    this.setData({ expenseDate: e.detail.value, fieldErrors: Object.assign({}, this.data.fieldErrors, { date: '' }) });
  },

  onNoteInput(e) {
    this.setData({ note: e.detail.value });
  },

  onCustomCategoryInput(e) {
    this.setData({ customCategoryName: e.detail.value });
  },

  addCustomCategory() {
    const name = (this.data.customCategoryName || '').trim();
    if (!name) return;
    post('/categories', { name }).then((category) => {
      const option = { value: category.categoryId, label: category.name };
      const exists = this.data.categoryOptions.some(item => item.value === option.value);
      this.setData({
        categoryOptions: exists ? this.data.categoryOptions : this.data.categoryOptions.concat(option),
        categoryId: option.value,
        customCategoryName: '',
        fieldErrors: Object.assign({}, this.data.fieldErrors, { category: '' }),
      });
    }).catch((err) => wx.showToast({ title: err.message || '创建科目失败', icon: 'none' }));
  },

  onSave() {
    const { expenseId, amountRaw, categoryId, expenseDate, note, saving } = this.data;
    if (saving) return;

    const fieldErrors = {
      amount: this._isPositiveAmount(amountRaw) ? '' : '请输入大于 0 的金额',
      category: categoryId ? '' : '请选择科目',
      date: expenseDate ? '' : '请选择日期',
    };
    if (fieldErrors.amount || fieldErrors.category || fieldErrors.date) {
      this.setData({ fieldErrors });
      return;
    }

    // Format amount to 2 decimal places using string arithmetic
    const formattedAmount = this._formatAmount(amountRaw);
    const body = {
      categoryId: Number(categoryId),
      amount: formattedAmount,
      expenseDate,
      note: note || null,
    };

    if (expenseId) {
      this._submit(expenseId, body);
      return;
    }
    this._checkMonthlyPlanThenSubmit(body);
  },

  _checkMonthlyPlanThenSubmit(body) {
    const month = body.expenseDate.slice(0, 7);
    if (this._isPlanWarningIgnored(month)) {
      this._submit(null, body);
      return;
    }
    get(`/plan/${month}`).then((plan) => {
      if (plan && plan.items && plan.items.length > 0) {
        this._submit(null, body);
        return;
      }
      wx.showModal({
        title: '本月未设置预算',
        content: '可先设置预算，也可以直接保存这笔开销。',
        confirmText: '直接保存',
        cancelText: '去设置',
        success: (res) => {
          if (res.confirm) {
            this._ignorePlanWarning(month);
            this._submit(null, body);
          } else {
            wx.navigateTo({ url: `/pages/plan/edit/edit?yearMonth=${month}` });
          }
        },
      });
    }).catch((err) => wx.showToast({ title: err.message || '检查预算失败', icon: 'none' }));
  },

  _submit(expenseId, body) {
    this.setData({ saving: true });
    const request = expenseId ? put(`/expense/${expenseId}`, body) : post('/expense', body);

    request
      .then(() => {
        wx.showToast({ title: expenseId ? '修改成功' : '记录成功', icon: 'success' });
        setTimeout(() => wx.navigateBack(), 800);
      })
      .catch((err) => {
        wx.showToast({ title: err.message || '保存失败', icon: 'none' });
      })
      .finally(() => this.setData({ saving: false }));
  },

  onDelete() {
    const { expenseId } = this.data;
    if (!expenseId) return;
    wx.showModal({
      title: '确认删除',
      content: '删除后将不再出现在账单中，确认删除这条记录吗？',
      confirmText: '删除',
      confirmColor: '#EF4444',
      success: (res) => {
        if (res.confirm) {
          del(`/expense/${expenseId}`)
            .then(() => {
              wx.showToast({ title: '已删除', icon: 'success' });
              setTimeout(() => wx.navigateBack(), 800);
            })
            .catch((err) => {
              wx.showToast({ title: err.message || '删除失败', icon: 'none' });
            });
        }
      },
    });
  },

  /**
   * Format a raw amount string to 2 decimal places.
   * Uses string manipulation to avoid float precision issues.
   */
  _formatAmount(raw) {
    if (!raw) return '0.00';
    if (raw.includes('.')) {
      const parts = raw.split('.');
      const dec = (parts[1] || '').padEnd(2, '0').slice(0, 2);
      return `${parts[0]}.${dec}`;
    }
    return `${raw}.00`;
  },

  _isPositiveAmount(raw) {
    const value = String(raw || '');
    if (!/^\d+(\.\d{1,2})?$/.test(value)) return false;
    const [whole, decimal = ''] = value.split('.');
    return whole !== '0' || /[1-9]/.test(decimal);
  },

  _isPlanWarningIgnored(month) {
    const userInfo = getApp().globalData.userInfo || {};
    return Boolean(wx.getStorageSync(`expensePlanWarningIgnored:${userInfo.userId || 'current'}:${month}`));
  },

  _ignorePlanWarning(month) {
    const userInfo = getApp().globalData.userInfo || {};
    wx.setStorageSync(`expensePlanWarningIgnored:${userInfo.userId || 'current'}:${month}`, true);
  },
});
