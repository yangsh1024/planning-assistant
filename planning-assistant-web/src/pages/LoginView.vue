<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { request } from '../api';

/** 创建浏览器登录请求并轮询小程序确认结果。 */
const emit = defineEmits<{ authenticated: [] }>();
const loginCode = ref('');
const fixedQrCodeUrl = ref('');
const loginError = ref('');
let timer: number | undefined;

type Login = {
  requestId: string;
  browserProof: string;
  mode: 'FIXED_QR_CODE';
  loginCode: string;
  fixedQrCodeUrl: string;
  expiresAt: string;
};
let login: Login | undefined;

/**
 * 创建新的浏览器登录请求并开始轮询。
 * <ol><li>清理旧轮询</li><li>创建请求</li><li>启动轮询</li></ol>
 */
async function create() {
  if (timer) clearInterval(timer);
  loginError.value = '';
  loginCode.value = '';
  fixedQrCodeUrl.value = '';
  try {
    login = await request<Login>('/api/web-auth/requests', { method: 'POST' });
    loginCode.value = login.loginCode;
    fixedQrCodeUrl.value = login.fixedQrCodeUrl;
    timer = window.setInterval(poll, 1500);
  } catch {
    loginError.value = '无法创建登录请求，请检查网络后重试。';
  }
}

/**
 * 轮询小程序对当前登录请求的确认结果。
 * <ol><li>查询状态</li><li>交换会话</li><li>停止轮询</li></ol>
 */
async function poll() {
  if (!login) return;
  try {
    const status = await request<{ status: string }>(`/api/web-auth/requests/${login.requestId}/status`, {
      headers: { 'X-Web-Login-Proof': login.browserProof },
    });
    if (status.status === 'APPROVED') {
      await request<void>(`/api/web-auth/requests/${login.requestId}/exchange`, {
        method: 'POST',
        body: JSON.stringify({ browserProof: login.browserProof }),
      });
      if (timer) clearInterval(timer);
      emit('authenticated');
    } else if (['REJECTED', 'EXPIRED', 'CONSUMED'].includes(status.status)) {
      if (timer) clearInterval(timer);
      loginError.value = status.status === 'REJECTED' ? '已在小程序拒绝登录。' : '登录请求已失效，请重新获取。';
    }
  } catch {
    if (timer) clearInterval(timer);
    loginError.value = '登录状态查询失败，请重新获取。';
  }
}

onMounted(create);
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <section>
    <h1>小猫账本</h1>
    <template v-if="loginCode">
      <p>使用微信扫描固定小程序码后，在确认页输入以下登录码。</p>
      <img :src="fixedQrCodeUrl" alt="固定小程序码">
      <output>{{ loginCode }}</output>
    </template>
    <p v-else-if="!loginError">正在生成登录请求…</p>
    <p v-if="loginError" class="error">{{ loginError }}</p>
    <button v-if="loginError" @click="create">重新获取</button>
    <p>登录请求 2 分钟内有效，确认后将自动进入对话。</p>
  </section>
</template>

<style scoped>
section { max-width: 480px; margin: 15vh auto; background: var(--surface); border-radius: 16px; padding: 32px; text-align: center; box-shadow: 0 10px 30px #02061733; }
img { width: 240px; height: 240px; object-fit: contain; }
output { display: block; font-size: 36px; font-weight: 700; letter-spacing: 8px; color: var(--primary); }
.error { color: #b91c1c; }
button { border: 0; border-radius: 8px; padding: 8px 14px; background: var(--primary); color: #fff; }
</style>
