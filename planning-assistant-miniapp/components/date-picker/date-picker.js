// components/date-picker/date-picker.js
Component({
  properties: {
    mode: {
      type: String,
      value: 'date', // 'date' = yyyy-MM-dd, 'month' = yyyy-MM
    },
    value: {
      type: String,
      value: '',
    },
    placeholder: {
      type: String,
      value: '请选择',
    },
    max: {
      type: String,
      value: '',
    },
    variant: {
      type: String,
      value: 'default', // default | compact | header
    },
  },

  data: {
    currentValue: '',
    showPicker: false,
    tempYear: 2026,
    tempMonth: '08',
    tempDay: 1,
    months: [
      { label: '1月', value: '01' }, { label: '2月', value: '02' },
      { label: '3月', value: '03' }, { label: '4月', value: '04' },
      { label: '5月', value: '05' }, { label: '6月', value: '06' },
      { label: '7月', value: '07' }, { label: '8月', value: '08' },
      { label: '9月', value: '09' }, { label: '10月', value: '10' },
      { label: '11月', value: '11' }, { label: '12月', value: '12' },
    ],
    dayHeaders: ['日', '一', '二', '三', '四', '五', '六'],
    dayItems: [],
  },

  lifetimes: {
    attached() {
      const now = new Date();
      const initValue = this.properties.value;
      if (initValue) {
        this._initFromValue(initValue);
      } else {
        const y = now.getFullYear();
        const m = String(now.getMonth() + 1).padStart(2, '0');
        const d = String(now.getDate()).padStart(2, '0');
        const defaultVal = this.properties.mode === 'month'
          ? `${y}-${m}`
          : `${y}-${m}-${d}`;
        this._initFromValue(defaultVal);
        this.setData({ currentValue: defaultVal });
        this.triggerEvent('change', { value: defaultVal });
      }
    },
  },

  observers: {
    'value': function(val) {
      if (val && val !== this.data.currentValue) {
        this._initFromValue(val);
      }
    },
  },

  methods: {
    _initFromValue(val) {
      if (!val) return;
      const parts = val.split('-');
      const year = parseInt(parts[0], 10);
      const month = parts[1] || '01';
      const day = parseInt(parts[2] || '1', 10);
      this.setData({
        currentValue: val,
        tempYear: year,
        tempMonth: month,
        tempDay: day,
      });
      if (this.properties.mode === 'date') {
        this._buildDayGrid(year, parseInt(month, 10));
      }
    },

    _buildDayGrid(year, month) {
      const firstDay = new Date(year, month - 1, 1).getDay(); // 0=Sun
      const daysInMonth = new Date(year, month, 0).getDate();
      const items = [];
      // Empty cells before first day
      for (let i = 0; i < firstDay; i++) {
        items.push({ empty: true, day: 0 });
      }
      for (let d = 1; d <= daysInMonth; d++) {
        items.push({ empty: false, day: d });
      }
      this.setData({ dayItems: items });
    },

    openPicker() {
      // Sync temp values from current before opening
      const val = this.data.currentValue;
      if (val) {
        const parts = val.split('-');
        const year = parseInt(parts[0], 10);
        const month = parts[1] || '01';
        const day = parseInt(parts[2] || '1', 10);
        this.setData({ tempYear: year, tempMonth: month, tempDay: day });
        if (this.properties.mode === 'date') {
          this._buildDayGrid(year, parseInt(month, 10));
        }
      }
      this.setData({ showPicker: true });
    },

    closePicker() {
      this.setData({ showPicker: false });
    },

    resetToValue() {
      this._initFromValue(this.properties.value);
    },

    stopProp() {
      // Prevents tap bubbling through the panel to the overlay
    },

    prevYear() {
      const y = this.data.tempYear - 1;
      this.setData({ tempYear: y });
      if (this.properties.mode === 'date') {
        this._buildDayGrid(y, parseInt(this.data.tempMonth, 10));
      }
    },

    nextYear() {
      const y = this.data.tempYear + 1;
      const maxYear = this.properties.max ? parseInt(this.properties.max.slice(0, 4), 10) : null;
      if (maxYear && y > maxYear) return;
      this.setData({ tempYear: y });
      if (this.properties.mode === 'date') {
        this._buildDayGrid(y, parseInt(this.data.tempMonth, 10));
      }
    },

    // Month mode: select month and auto-confirm
    selectMonth(e) {
      const month = e.currentTarget.dataset.month;
      this.setData({ tempMonth: month });
    },

    // Date mode: select month and rebuild day grid
    selectDateMonth(e) {
      const month = e.currentTarget.dataset.month;
      this.setData({ tempMonth: month, tempDay: 1 });
      this._buildDayGrid(this.data.tempYear, parseInt(month, 10));
    },

    selectDay(e) {
      const day = e.currentTarget.dataset.day;
      if (!day) return;
      this.setData({ tempDay: day });
    },

    confirmPick() {
      const { tempYear, tempMonth, tempDay, mode } = this.data;
      let value;
      if (mode === 'month') {
        value = `${tempYear}-${tempMonth}`;
      } else {
        const d = String(tempDay).padStart(2, '0');
        value = `${tempYear}-${tempMonth}-${d}`;
      }
      if (this.properties.max && value > this.properties.max) {
        wx.showToast({ title: '不能选择未来日期', icon: 'none' });
        return;
      }
      this.setData({ currentValue: value, showPicker: false });
      this.triggerEvent('change', { value });
    },
  },
});
