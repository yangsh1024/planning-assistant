// components/amount-input/amount-input.js
Component({
  properties: {
    value: {
      type: String,
      value: '',
    },
  },

  data: {
    displayValue: '',
  },

  lifetimes: {
    attached() {
      this.setData({ displayValue: this.properties.value || '' });
    },
  },

  observers: {
    'value': function(value) {
      const normalized = value || '';
      if (normalized !== this.data.displayValue) {
        this.setData({ displayValue: normalized });
      }
    },
  },

  methods: {
    onInput(e) {
      const value = this._sanitizeAmount(e.detail.value);
      this.setData({ displayValue: value });
      this.triggerEvent('change', { value });
    },

    _sanitizeAmount(raw) {
      let value = '';
      let hasDecimal = false;
      let decimalLength = 0;
      for (const char of String(raw || '')) {
        if (/\d/.test(char)) {
          if (hasDecimal) {
            if (decimalLength >= 2) continue;
            decimalLength += 1;
          }
          if (value.replace('.', '').length >= 10) continue;
          value += char;
        } else if (char === '.' && !hasDecimal) {
          value += char;
          hasDecimal = true;
        }
      }
      return value;
    },
  },
});
