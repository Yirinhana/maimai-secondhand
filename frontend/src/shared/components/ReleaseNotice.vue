<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';
const available = ref(false), busy = ref(false);
let timer: ReturnType<typeof setInterval> | undefined;
async function check() {
  if (import.meta.env.DEV || document.hidden || busy.value) return;
  busy.value = true;
  try {
    const response = await fetch('/release.json', { cache: 'no-store', credentials: 'omit' });
    if (!response.ok) return;
    const release = await response.json();
    available.value = typeof release.build === 'string' && release.build !== __MAIMAI_BUILD__;
  } catch { /* Offline use and failed update checks must never interrupt a form. */ }
  finally { busy.value = false; }
}
function refresh() { window.location.reload(); }
onMounted(() => { if (!import.meta.env.DEV) { timer = setInterval(check, 120000); window.addEventListener('focus', check); void check(); } });
onBeforeUnmount(() => { if (timer) clearInterval(timer); window.removeEventListener('focus', check); });
</script>
<template><aside v-if="available" class="release-notice" role="status"><div><strong>网站有新版本</strong><p>请先保存正在填写的内容，再刷新体验。</p></div><button type="button" @click="refresh">刷新更新</button><button type="button" aria-label="稍后更新" @click="available=false">×</button></aside></template>
<style scoped>.release-notice{position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:1800;display:flex;align-items:center;gap:14px;padding:14px 18px;border:1px solid #d6ded9;border-radius:14px;background:#fffefb;box-shadow:0 8px 32px #18271e24;max-width:calc(100vw - 32px);width:max-content;box-sizing:border-box;color:#32423a}.release-notice strong{font-size:14px}.release-notice p{margin:4px 0 0;font-size:12px;line-height:1.7}.release-notice button{flex-shrink:0;border:0;background:#eaf0eb;border-radius:8px;min-height:38px;padding:8px 12px;cursor:pointer;color:inherit}.release-notice button:last-child{background:none;font-size:20px;padding:6px}.release-notice>div{min-width:0}@media(max-width:480px){.release-notice{bottom:calc(76px + env(safe-area-inset-bottom));padding:12px;gap:8px}.release-notice p{font-size:11px}}</style>
