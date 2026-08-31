import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import './styles.css';
// 在入口统一注入状态容器，所有页面共享同一份登录状态。
createApp(App).use(createPinia()).mount('#app');
