/** 通用选项选择组件，保持外部值与展示标签在异步更新时同步。 */
Component({
  properties: {
    options: {
      type: Array,
      value: [],
    },
    value: {
      type: null,
      value: null,
    },
    placeholder: {
      type: String,
      value: '请选择',
    },
  },

  data: {
    currentValue: null,
    currentLabel: '',
    showPicker: false,
  },

  lifetimes: {
    attached() {
      this._syncLabel(this.properties.value);
    },
  },

  observers: {
    'value': function(val) {
      this._syncLabel(val);
    },
    'options': function() {
      this._syncLabel(this.data.currentValue);
    },
  },

  methods: {
    _syncLabel(val) {
      if (val === null || val === undefined) {
        this.setData({ currentValue: null, currentLabel: '' });
        return;
      }
      const matched = (this.properties.options || []).find(o => String(o.value) === String(val));
      this.setData({
        currentValue: val,
        currentLabel: matched ? matched.label : '',
      });
    },

    openPicker() {
      this.setData({ showPicker: true });
    },

    closePicker() {
      this.setData({ showPicker: false });
    },

    stopProp() {},

    selectOption(e) {
      const { value, label } = e.currentTarget.dataset;
      this.setData({
        currentValue: value,
        currentLabel: label,
        showPicker: false,
      });
      this.triggerEvent('change', { value, label });
    },
  },
});
