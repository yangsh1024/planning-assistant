/** 统一展示加载、空数据和失败状态，保证页面使用一致的重试入口。 */
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
