// pages/expense/stats/stats.js
const { get } = require('../../../utils/request');
const { formatYearMonth } = require('../../../utils/date');

// Pie chart color palette (one per category, derived from the accent hue family)
const CHART_COLORS = [
  '#0EA5E9', '#38BDF8', '#7DD3FC', '#F59E0B',
  '#FCA5A5', '#10B981', '#F97316', '#8B5CF6',
];

Page({
  data: {
    currentYearMonth: '',
    maxYearMonth: '',
    filterCategoryId: null,
    filterCategoryName: '',
    filterOptions: [{ value: '', label: '全部科目' }],
    filterIndex: 0,
    filterLabel: '全部科目',

    activeTab: 'stats', // 'stats' | 'list'

    // Stats tab
    pageStatus: 'loading',
    pageMessage: '',
    grandTotal: '0.00',
    totalCount: 0,
    dailyAvg: '0.00',
    statItems: [],    // [{categoryId, categoryName, total, count, percentage, color}]
    budgetSummary: null,

    // List tab
    expenseGroups: [], // [{date, dateLabel, dayTotal, list:[]}]
    listStatus: 'loading',
    listMessage: '',
    currentPage: 1,
    pageSize: 20,
    totalExpenses: 0,
    loadingMore: false,
    noMore: false,
    refreshing: false,
  },

  onLoad(options) {
    if (!getApp().checkAuth()) return;

    const now = new Date();
    const pendingNavigation = this._takePendingNavigation();
    const ym = (pendingNavigation && pendingNavigation.yearMonth)
      || options.yearMonth
      || formatYearMonth(now.getFullYear(), now.getMonth() + 1);
    const catId = options.categoryId ? Number(options.categoryId) : null;
    const catName = options.categoryName ? decodeURIComponent(options.categoryName) : '';

    this.setData({
      currentYearMonth: ym,
      maxYearMonth: formatYearMonth(now.getFullYear(), now.getMonth() + 1),
      filterCategoryId: catId,
      filterCategoryName: catName,
    });

    this._loadCategories(catId);
    if (catName || options.tab === 'list' || (pendingNavigation && pendingNavigation.tab === 'list')) {
      const title = catName ? catName + ' 明细' : '账单';
      wx.setNavigationBarTitle({ title });
      // If navigated from category, go straight to list tab
      this.setData({ activeTab: 'list' });
      this.loadList(true);
    } else {
      this.loadData();
    }
  },

  onShow() {
    const pendingNavigation = this._takePendingNavigation();
    if (pendingNavigation) {
      this._openPendingNavigation(pendingNavigation);
      return;
    }
    // Refresh list when returning from edit
    if (this.data.activeTab === 'list') {
      this.loadList(true);
    } else {
      this.loadData();
    }
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.activeTab) return;
    this.setData({ activeTab: tab });
    if (tab === 'stats') {
      this.loadData();
    } else {
      this.loadList(true);
    }
  },

  loadData() {
    this._chartRenderToken = (this._chartRenderToken || 0) + 1;
    this.setData({ pageStatus: 'loading', pageMessage: '' });
    Promise.all([
      get(`/expense/stats/${this.data.currentYearMonth}`),
      get(`/plan/${this.data.currentYearMonth}/summary`),
    ])
      .then(([stats, budgetSummary]) => {
        const order = new Map(((budgetSummary && budgetSummary.items) || []).map((item, index) => [
          String(item.categoryId), index,
        ]));
        const items = ((stats && stats.items) || []).map((item, index) => Object.assign({}, item, { _index: index }))
          .sort((a, b) => {
            const aOrder = order.has(String(a.categoryId)) ? order.get(String(a.categoryId)) : Number.MAX_SAFE_INTEGER;
            const bOrder = order.has(String(b.categoryId)) ? order.get(String(b.categoryId)) : Number.MAX_SAFE_INTEGER;
            return aOrder === bOrder ? a._index - b._index : aOrder - bOrder;
          });
        if (items.length === 0) {
          this.setData({ pageStatus: 'empty', pageMessage: '本月暂无支出记录', statItems: [], grandTotal: '0.00', totalCount: 0, dailyAvg: '0.00', budgetSummary: null });
          return;
        }
        const statItems = items.map((item, index) => {
          const statItem = Object.assign({}, item);
          delete statItem._index;
          return Object.assign(statItem, { color: CHART_COLORS[index % CHART_COLORS.length] });
        });
        // `statItems` controls the canvas node via wx:if.  On a physical device
        // the node is not necessarily available until this setData has rendered.
        this.setData({
          pageStatus: 'done',
          grandTotal: stats.grandTotal,
          totalCount: stats.totalCount,
          dailyAvg: stats.dailyAvg,
          statItems,
          budgetSummary: budgetSummary && budgetSummary.totalBudget !== '0.00' ? budgetSummary : null,
        }, () => {
          this._drawPieChart(statItems);
        });
      })
      .catch((err) => {
        this.setData({ pageStatus: 'error', pageMessage: err.message || '加载失败' });
      })
      .finally(() => {
        this.setData({ refreshing: false });
      });
  },

  loadList(reset = false) {
    if (reset) {
      this.setData({
        expenseGroups: [],
        currentPage: 1,
        noMore: false,
        loadingMore: false,
        listStatus: 'loading',
        listMessage: '',
      });
    }

    if (this.data.noMore && !reset) return;

    const { currentPage, pageSize, currentYearMonth, filterCategoryId } = this.data;
    const params = { yearMonth: currentYearMonth, page: currentPage, pageSize };
    if (filterCategoryId) params.categoryId = filterCategoryId;

    get('/expense', params)
      .then((data) => {
        const list = (data && data.list) || [];
        const total = (data && data.total) || 0;
        const newGroups = this._groupByDate(list);

        // Merge groups if paginating
        let mergedGroups;
        if (reset || currentPage === 1) {
          mergedGroups = newGroups;
        } else {
          mergedGroups = this._mergeGroups(this.data.expenseGroups, newGroups);
        }

        const noMore = (currentPage * pageSize) >= total;

        this.setData({
          expenseGroups: mergedGroups,
          totalExpenses: total,
          noMore,
          loadingMore: false,
          listStatus: list.length === 0 ? 'done' : 'done',
        });
      })
      .catch((err) => {
        this.setData({ listStatus: 'error', listMessage: err.message || '加载失败', loadingMore: false });
      })
      .finally(() => {
        this.setData({ refreshing: false });
      });
  },

  onLoadMore() {
    if (this.data.noMore || this.data.loadingMore) return;
    this.setData({
      currentPage: this.data.currentPage + 1,
      loadingMore: true,
    });
    this.loadList(false);
  },

  onRefresh() {
    this.setData({ refreshing: true });
    if (this.data.activeTab === 'stats') {
      this.loadData();
    } else {
      this.loadList(true);
    }
  },

  onYearMonthChange(e) {
    const yearMonth = e.detail.value;
    if (!yearMonth || yearMonth === this.data.currentYearMonth) return;
    this.setData({
      currentYearMonth: yearMonth,
      filterCategoryId: null,
      filterCategoryName: '',
      filterIndex: 0,
      filterLabel: '全部科目',
      expenseGroups: [],
      statItems: [],
    });
    if (this.data.activeTab === 'stats') this.loadData();
    else this.loadList(true);
    this._loadCategories();
  },

  onCategoryFilterChange(e) {
    const index = Number(e.detail.value);
    const option = this.data.filterOptions[index];
    this.setData({
      filterIndex: index,
      filterCategoryId: option && option.value ? Number(option.value) : null,
      filterCategoryName: option ? option.label : '',
      filterLabel: option ? option.label : '全部科目',
    });
    this.loadList(true);
  },

  onExpenseTap(e) {
    const expense = e.currentTarget.dataset.expense;
    wx.navigateTo({
      url: `/pages/expense/add/add?expenseId=${expense.expenseId}&categoryId=${expense.categoryId}&amount=${expense.amount}&expenseDate=${expense.expenseDate}&note=${encodeURIComponent(expense.note || '')}`,
    });
  },

  // ─── Helpers ────────────────────────────────────────────────────────────────

  _loadCategories(selectedId) {
    Promise.all([
      get('/categories'),
      get(`/plan/${this.data.currentYearMonth}`),
    ]).then(([categories, plan]) => {
      const planOrder = new Map((plan && plan.items ? plan.items : []).map((item, index) => [
        String(item.categoryId), index,
      ]));
      const orderedCategories = (categories || []).slice().sort((a, b) => {
        const aOrder = planOrder.has(String(a.categoryId)) ? planOrder.get(String(a.categoryId)) : Number.MAX_SAFE_INTEGER;
        const bOrder = planOrder.has(String(b.categoryId)) ? planOrder.get(String(b.categoryId)) : Number.MAX_SAFE_INTEGER;
        return aOrder - bOrder;
      });
      const filterOptions = [{ value: '', label: '全部科目' }].concat(orderedCategories.map(item => ({
        value: item.categoryId,
        label: item.name,
      })));
      const filterIndex = selectedId
        ? Math.max(0, filterOptions.findIndex(item => String(item.value) === String(selectedId)))
        : 0;
      this.setData({ filterOptions, filterIndex, filterLabel: filterOptions[filterIndex].label });
    }).catch(() => {});
  },

  _takePendingNavigation() {
    const app = getApp();
    const pendingNavigation = app.globalData.pendingStatsNavigation;
    app.globalData.pendingStatsNavigation = null;
    return pendingNavigation;
  },

  _openPendingNavigation(navigation) {
    const now = new Date();
    const yearMonth = navigation.yearMonth || formatYearMonth(now.getFullYear(), now.getMonth() + 1);
    this.setData({
      currentYearMonth: yearMonth,
      maxYearMonth: formatYearMonth(now.getFullYear(), now.getMonth() + 1),
      filterCategoryId: null,
      filterCategoryName: '',
      filterIndex: 0,
      filterLabel: '全部科目',
      activeTab: navigation.tab === 'list' ? 'list' : 'stats',
      expenseGroups: [],
      statItems: [],
    });
    this._loadCategories();
    if (navigation.tab === 'list') {
      this.loadList(true);
    } else {
      this.loadData();
    }
  },

  _drawPieChart(items) {
    if (!items || items.length === 0) return;
    const renderToken = this._chartRenderToken || 0;
    // The legacy Canvas context is a separate native layer.  Inside a
    // scroll-view it can remain in the old position after pull-to-refresh.
    // Use a 2D canvas node, which participates in the normal view layout.
    wx.nextTick(() => {
      const query = wx.createSelectorQuery().in(this);
      query.select('#pieChart').fields({ node: true, size: true }).exec((result) => {
        if (renderToken !== this._chartRenderToken) return;
        const canvasInfo = result && result[0];
        if (!canvasInfo || !canvasInfo.node || !canvasInfo.width || !canvasInfo.height) return;

        const canvas = canvasInfo.node;
        const ctx = canvas.getContext('2d');
        const dpr = wx.getSystemInfoSync().pixelRatio || 1;
        const size = Math.min(canvasInfo.width, canvasInfo.height);

        canvas.width = size * dpr;
        canvas.height = size * dpr;
        ctx.scale(dpr, dpr);
        ctx.clearRect(0, 0, size, size);

        const cx = size / 2;
        const cy = size / 2;
        const r = size * 0.42;
        const innerR = size * 0.27;
        let startAngle = -Math.PI / 2;

        items.forEach(item => {
          const slice = (item.percentage / 100) * 2 * Math.PI;
          ctx.beginPath();
          ctx.moveTo(cx, cy);
          ctx.arc(cx, cy, r, startAngle, startAngle + slice);
          ctx.closePath();
          ctx.fillStyle = item.color;
          ctx.fill();
          startAngle += slice;
        });

        ctx.beginPath();
        ctx.arc(cx, cy, innerR, 0, 2 * Math.PI);
        ctx.fillStyle = '#FFFFFF';
        ctx.fill();
      });
    });
  },

  _groupByDate(list) {
    const groups = {};
    list.forEach(item => {
      const date = item.expenseDate;
      if (!groups[date]) {
        groups[date] = { date, dateLabel: this._toDateLabel(date), dayTotalCents: 0, list: [] };
      }
      groups[date].list.push(item);
      groups[date].dayTotalCents += this._toCents(item.amount);
    });

    return Object.values(groups)
      .sort((a, b) => b.date.localeCompare(a.date))
      .map(g => ({
        date: g.date,
        dateLabel: g.dateLabel,
        dayTotal: this._fromCents(g.dayTotalCents),
        list: g.list,
      }));
  },

  _mergeGroups(existing, incoming) {
    const map = {};
    existing.forEach(g => { map[g.date] = Object.assign({}, g, { list: g.list.slice() }); });
    incoming.forEach(g => {
      if (map[g.date]) {
        // Merge lists (avoid duplicates by expenseId)
        const ids = new Set(map[g.date].list.map(e => e.expenseId));
        g.list.forEach(e => { if (!ids.has(e.expenseId)) map[g.date].list.push(e); });
        map[g.date].dayTotal = this._sumExpenseAmounts(map[g.date].list);
      } else {
        map[g.date] = g;
      }
    });
    return Object.values(map).sort((a, b) => b.date.localeCompare(a.date));
  },

  // Convert amount string like "68.50" to integer cents (no float ops)
  _toCents(amount) {
    if (!amount) return 0;
    const s = String(amount);
    if (s.includes('.')) {
      const parts = s.split('.');
      const intPart = parseInt(parts[0], 10) || 0;
      const decStr = (parts[1] || '').padEnd(2, '0').slice(0, 2);
      return intPart * 100 + parseInt(decStr, 10);
    }
    return parseInt(s, 10) * 100;
  },

  _fromCents(cents) {
    const negative = cents < 0;
    const abs = Math.abs(cents);
    const intPart = Math.floor(abs / 100);
    const decPart = String(abs % 100).padStart(2, '0');
    return `${negative ? '-' : ''}${intPart}.${decPart}`;
  },

  _sumExpenseAmounts(expenses) {
    return this._fromCents((expenses || []).reduce((total, expense) => total + this._toCents(expense.amount), 0));
  },

  _toDateLabel(dateStr) {
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;
    const yesterday = new Date(now - 86400000);
    const yd = `${yesterday.getFullYear()}-${String(yesterday.getMonth()+1).padStart(2,'0')}-${String(yesterday.getDate()).padStart(2,'0')}`;
    if (dateStr === today) return '今天';
    if (dateStr === yd) return '昨天';
    const parts = dateStr.split('-');
    return `${parseInt(parts[1], 10)}月${parseInt(parts[2], 10)}日`;
  },

});
