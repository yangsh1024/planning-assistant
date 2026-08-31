/** 编辑指定月份的预算分类和额度，并可继承最近的历史方案作为起点。 */
const { get, post } = require('../../../utils/request');
const { formatYearMonth } = require('../../../utils/date');

Page({
  data: {
    currentYearMonth: '',
    maxYearMonth: '',
    pageStatus: 'loading', // 'loading' | 'ready' | 'error'
    pageMessage: '',
    items: [],        // [{categoryId, categoryName, amount}]
    categories: [],   // all available categories
    addedIds: {},     // {categoryId: true} for already-added check
    inherited: false, // true if auto-copied from prev month
    saving: false,
    customCatName: '',
    initialSnapshot: '[]',
    // Whether this page was opened because first-expense requires a plan
    requirePlan: false,
    returnTo: '',
    draggingIndex: -1,
    dragTargetIndex: -1,
  },

  onLoad(options) {
    if (!getApp().checkAuth()) return;

    const now = new Date();
    const ym = options.yearMonth ||
      `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    const maxYearMonth = formatYearMonth(now.getFullYear(), now.getMonth() + 1);

    this.setData({
      currentYearMonth: ym,
      maxYearMonth,
      requirePlan: options.requirePlan === '1',
      returnTo: options.returnTo || '',
    });

    if (this.data.requirePlan) {
      wx.setNavigationBarTitle({ title: '设置预算后继续' });
    }

    this.loadPlan();
  },

  loadPlan() {
    // 当月无计划时仅继承最近方案的结构，用户仍需主动保存确认。
    this.setData({ pageStatus: 'loading', pageMessage: '', inherited: false });
    const ym = this.data.currentYearMonth;

    Promise.all([
      get(`/plan/${ym}`),
      get('/categories'),
    ])
      .then(([plan, cats]) => {
        const categories = (cats || []).map(c => ({
          categoryId: c.categoryId,
          name: c.name,
        }));

        if (plan && plan.items && plan.items.length > 0) {
          this._applyPlan(plan.items, categories, false);
        } else {
          this._findClosestPlan(ym)
            .then((prevPlan) => {
              if (prevPlan && prevPlan.items && prevPlan.items.length > 0) {
                this._applyPlan(prevPlan.items, categories, true);
              } else {
                this._applyPlan([], categories, false);
              }
            })
            .catch(() => {
              this._applyPlan([], categories, false);
            });
        }
      })
      .catch((err) => {
        this.setData({ pageStatus: 'error', pageMessage: err.message || '加载失败' });
      });
  },

  _applyPlan(planItems, categories, inherited) {
    const items = planItems.map(i => ({
      categoryId: i.categoryId,
      categoryName: i.categoryName || this._findName(categories, i.categoryId),
      amount: i.amount || '',
      sortOrder: i.sortOrder,
      error: '',
    }));

    const addedIds = {};
    items.forEach(i => { addedIds[i.categoryId] = true; });

    this.setData({
      items,
      categories,
      addedIds,
      inherited,
      pageStatus: 'ready',
    }, () => this.setData({ initialSnapshot: this._serializeItems(this.data.items) }));
  },

  _findName(categories, id) {
    const cat = categories.find(c => c.categoryId === id);
    return cat ? cat.name : '';
  },

  onYearMonthChange(e) {
    const yearMonth = e.detail.value;
    if (!yearMonth || yearMonth === this.data.currentYearMonth) return;
    const switchMonth = () => {
      this.setData({
        currentYearMonth: yearMonth,
      });
      this.loadPlan();
    };
    if (!this._isDirty()) {
      switchMonth();
      return;
    }
    wx.showModal({
      title: '数据未保存',
      content: '是否切换月份？未保存的修改将丢失。',
      confirmText: '切换',
      cancelText: '继续编辑',
      success: (res) => {
        if (res.confirm) {
          switchMonth();
          return;
        }
        const picker = this.selectComponent('#plan-month-picker');
        if (picker) picker.resetToValue();
      },
    });
  },

  addItem(e) {
    const cat = e.currentTarget.dataset.cat;
    if (this.data.addedIds[cat.categoryId]) return;

    const items = this.data.items.concat({
      categoryId: cat.categoryId,
      categoryName: cat.name,
      amount: '',
      error: '',
    });
    const addedIds = Object.assign({}, this.data.addedIds);
    addedIds[cat.categoryId] = true;
    this.setData({ items, addedIds });
  },

  removeItem(e) {
    const index = e.currentTarget.dataset.index;
    const items = this.data.items.slice();
    const removed = items.splice(index, 1)[0];
    const addedIds = Object.assign({}, this.data.addedIds);
    delete addedIds[removed.categoryId];
    this.setData({ items, addedIds });
  },

  onDragStart(e) {
    const touch = e.touches && e.touches[0];
    if (!touch) return;
    this._dragStartY = touch.pageY;
    this._dragSourceIndex = Number(e.currentTarget.dataset.index);
    this.setData({
      draggingIndex: this._dragSourceIndex,
      dragTargetIndex: this._dragSourceIndex,
      items: this._withDragVisual(this._dragSourceIndex, this._dragSourceIndex, 0),
    });
  },

  onDragMove(e) {
    const touch = e.touches && e.touches[0];
    if (!touch || this._dragSourceIndex == null || this._dragStartY == null) return;

    const offset = touch.pageY - this._dragStartY;
    const sourceIndex = this._dragSourceIndex;
    const targetIndex = Math.max(0, Math.min(
      this.data.items.length - 1,
      sourceIndex + Math.round(offset / 70),
    ));

    this.setData({
      dragTargetIndex: targetIndex,
      items: this._withDragVisual(sourceIndex, targetIndex, offset),
    });
  },

  onDragEnd(e) {
    const sourceIndex = this._dragSourceIndex;
    const targetIndex = this.data.dragTargetIndex;
    if (sourceIndex == null || targetIndex < 0) {
      this._resetDrag();
      return;
    }

    const items = this.data.items.map((entry) => {
      const item = Object.assign({}, entry);
      delete item.dragShift;
      return item;
    });
    this._dragStartY = null;
    this._dragSourceIndex = null;
    if (targetIndex !== sourceIndex) {
      const [moving] = items.splice(sourceIndex, 1);
      items.splice(targetIndex, 0, moving);
    }
    this.setData({ items, draggingIndex: -1, dragTargetIndex: -1 });
  },

  onDragCancel() {
    this._resetDrag();
  },

  _withDragVisual(sourceIndex, targetIndex, offset) {
    const rowHeight = 70;
    return this.data.items.map((item, index) => {
      let dragShift = 0;
      if (index === sourceIndex) {
        dragShift = offset;
      } else if (sourceIndex < targetIndex && index > sourceIndex && index <= targetIndex) {
        dragShift = -rowHeight;
      } else if (targetIndex < sourceIndex && index >= targetIndex && index < sourceIndex) {
        dragShift = rowHeight;
      }
      return Object.assign({}, item, { dragShift });
    });
  },

  _resetDrag() {
    this._dragStartY = null;
    this._dragSourceIndex = null;
    this.setData({
      items: this.data.items.map((entry) => {
        const item = Object.assign({}, entry);
        delete item.dragShift;
        return item;
      }),
      draggingIndex: -1,
      dragTargetIndex: -1,
    });
  },

  onCustomCatInput(e) {
    this.setData({ customCatName: e.detail.value });
  },

  addCustomCategory() {
    const name = (this.data.customCatName || '').trim();
    if (!name) return;

    post('/categories', { name })
      .then((cat) => {
        const newCat = { categoryId: cat.categoryId, name: cat.name };
        const categoryExists = this.data.categories.some(item => item.categoryId === cat.categoryId);
        const alreadyAdded = this.data.addedIds[cat.categoryId];
        const categories = categoryExists ? this.data.categories : this.data.categories.concat(newCat);
        const items = alreadyAdded ? this.data.items : this.data.items.concat({
          categoryId: cat.categoryId,
          categoryName: cat.name,
          amount: '',
          error: '',
        });
        const addedIds = Object.assign({}, this.data.addedIds);
        addedIds[cat.categoryId] = true;
        this.setData({ categories, items, addedIds, customCatName: '' });
        if (alreadyAdded) wx.showToast({ title: '该科目已添加', icon: 'none' });
      })
      .catch((err) => {
        wx.showToast({ title: err.message || '添加失败', icon: 'none' });
      });
  },

  onAmountInput(e) {
    const index = e.currentTarget.dataset.index;
    const items = this.data.items.slice();
    items[index] = Object.assign({}, items[index], { amount: e.detail.value, error: '' });
    this.setData({ items });
  },

  onSave() {
    if (this.data.saving) return;

    const items = this.data.items;
    if (items.length === 0) {
      wx.showToast({ title: '请至少添加一个科目', icon: 'none' });
      return;
    }

    const validatedItems = items.map(item => Object.assign({}, item, {
      error: this._isPositiveAmount(item.amount) ? '' : '金额必须大于 0',
    }));
    if (validatedItems.some(item => item.error)) {
      this.setData({ items: validatedItems });
      return;
    }

    this.setData({ saving: true });

    const body = {
      items: validatedItems.map((i, index) => ({
        categoryId: i.categoryId,
        amount: this._formatAmount(i.amount),
        sortOrder: index,
      })),
    };

    post(`/plan/${this.data.currentYearMonth}`, body)
      .then(() => {
        wx.showToast({ title: '保存成功', icon: 'success' });
        setTimeout(() => {
          if (this.data.requirePlan && this.data.returnTo === 'expense') {
            wx.redirectTo({ url: `/pages/expense/add/add?yearMonth=${this.data.currentYearMonth}` });
            return;
          }
          wx.navigateBack();
        }, 800);
      })
      .catch((err) => {
        wx.showToast({ title: err.message || '保存失败', icon: 'none' });
      })
      .finally(() => {
        this.setData({ saving: false });
      });
  },

  _prevYearMonth(ym) {
    const [y, m] = ym.split('-').map(Number);
    const d = new Date(y, m - 2, 1);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
  },

  _findClosestPlan(yearMonth) {
    const tryMonth = (candidate, remaining) => {
      if (remaining <= 0) return Promise.resolve(null);
      return get(`/plan/${candidate}`).then((plan) => {
        if (plan && plan.items && plan.items.length > 0) return plan;
        return tryMonth(this._prevYearMonth(candidate), remaining - 1);
      });
    };
    return tryMonth(this._prevYearMonth(yearMonth), 60);
  },

  _serializeItems(items) {
    return JSON.stringify((items || []).map(item => ({
      categoryId: item.categoryId,
      amount: item.amount || '',
    })));
  },

  _isDirty() {
    return this._serializeItems(this.data.items) !== this.data.initialSnapshot;
  },

  _isPositiveAmount(raw) {
    const value = String(raw || '');
    if (!/^\d+(\.\d{1,2})?$/.test(value)) return false;
    const [whole, decimal = ''] = value.split('.');
    return whole !== '0' || /[1-9]/.test(decimal);
  },

  _formatAmount(raw) {
    if (!raw) return '0.00';
    const s = String(raw);
    if (s.includes('.')) {
      const parts = s.split('.');
      const dec = (parts[1] || '').padEnd(2, '0').slice(0, 2);
      return `${parts[0]}.${dec}`;
    }
    return `${s}.00`;
  },

});
