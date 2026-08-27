// components/state-view/state-view.js
Component({
  properties: {
    status: {
      type: String,
      value: 'done', // 'loading' | 'empty' | 'error' | 'done'
    },
    message: {
      type: String,
      value: '',
    },
    showRetry: {
      type: Boolean,
      value: true,
    },
  },

  methods: {
    onRetry() {
      this.triggerEvent('retry');
    },
  },
});
