<script setup lang="ts"> defineProps<{text:string}>(); </script><template><p class="state">{{ text }}</p></template><style scoped>.state{color:var(--muted);text-align:center;padding:32px}</style>
