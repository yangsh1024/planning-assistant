<script setup lang="ts">
import { computed } from 'vue';
import DOMPurify from 'dompurify';
import { marked } from 'marked';
import { MARKDOWN_ALLOWED_ATTR, MARKDOWN_ALLOWED_TAGS } from '../markdownPolicy';

const props = defineProps<{ role:'USER'|'ASSISTANT'; content:string; failed?:boolean }>();
const rendered = computed(() => {
  const sanitized = DOMPurify.sanitize(marked.parse(props.content) as string, {
    ALLOWED_TAGS: MARKDOWN_ALLOWED_TAGS,
    ALLOWED_ATTR: MARKDOWN_ALLOWED_ATTR,
    ALLOW_DATA_ATTR: false
  });
  const documentFragment = new DOMParser().parseFromString(sanitized, 'text/html');
  documentFragment.querySelectorAll('a').forEach(link => {
    const href = link.getAttribute('href') || '';
    if (!/^(https?:\/\/|\/)/i.test(href)) link.removeAttribute('href');
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
  });
  return documentFragment.body.innerHTML;
});
</script>
<template><article class="bubble" :class="role==='USER'?'user':'assistant'"><p v-if="role==='USER'" class="plain">{{content}}</p><div v-else class="markdown" v-html="rendered"/><small v-if="failed">发送失败，可重新发送。</small></article></template>
<style scoped>.bubble{max-width:76%;padding:12px 16px;border-radius:16px;margin:12px 0;overflow-wrap:anywhere}.plain{margin:0;white-space:pre-wrap}.user{background:var(--primary);color:#fff;margin-left:auto}.assistant{background:var(--surface);border:1px solid var(--border)}.markdown :deep(p:first-child){margin-top:0}.markdown :deep(p:last-child){margin-bottom:0}.markdown :deep(pre){overflow:auto;padding:12px;border-radius:8px;background:#0f172a;color:#e5e7eb}.markdown :deep(a){color:var(--primary)}small{display:block;margin-top:8px}</style>
