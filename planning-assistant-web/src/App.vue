<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import ChatView from './pages/ChatView.vue';
import LoginView from './pages/LoginView.vue';
import { exchangeTicket, sessions } from './api';
import { useAuthStore } from './stores/auth';
// 先消费 fragment 中的一次性票据，再清除地址栏以减少票据暴露时间。
const auth=useAuthStore();
const markUnauthorized=()=>auth.setAuthenticated(false);
onMounted(async()=>{ window.addEventListener('agent-unauthorized',markUnauthorized); const ticket=new URLSearchParams(location.hash.replace(/^#/,'')).get('ticket'); try { if(ticket){await exchangeTicket(ticket); history.replaceState(null,'',location.pathname);} await sessions(); auth.setAuthenticated(true); } catch { auth.setAuthenticated(false); } finally { auth.setLoading(false); }});
onUnmounted(()=>window.removeEventListener('agent-unauthorized',markUnauthorized));
</script>
<template><main v-if="!auth.loading"><ChatView v-if="auth.authenticated"/><LoginView v-else @authenticated="auth.setAuthenticated(true)"/></main></template>
