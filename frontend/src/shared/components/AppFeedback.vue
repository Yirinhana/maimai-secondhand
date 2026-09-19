<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { navigationFeedback } from '../navigationFeedback';
const online = ref(navigator.onLine);
function updateNetwork() {
  online.value = navigator.onLine;
}
function retryNavigation() {
  window.location.assign(navigationFeedback.failedPath);
}
onMounted(() => {
  window.addEventListener('online', updateNetwork);
  window.addEventListener('offline', updateNetwork);
});
onUnmounted(() => {
  window.removeEventListener('online', updateNetwork);
  window.removeEventListener('offline', updateNetwork);
});
</script>
<template>
  <div
    v-if="navigationFeedback.loading"
    class="mm-route-progress"
    role="status"
    aria-label="正在打开页面"
  >
    <span />
  </div>
  <div v-if="!online" class="mm-connectivity" role="status">
    <span
      class="mm-connectivity__dot"
      aria-hidden="true"
    />网络已断开，请检查连接。恢复后可重试当前操作。
  </div>
  <section
    v-if="navigationFeedback.failedPath"
    class="mm-navigation-error"
    role="alert"
  >
    <div>
      <strong>这个页面暂时没有打开</strong>
      <p>
        可能是网络中断或网站刚刚更新。当前页面仍保留，可重新打开目标页面。
      </p>
    </div>
    <button type="button" @click="retryNavigation">重新打开</button>
    <button
      type="button"
      class="mm-navigation-error__dismiss"
      @click="navigationFeedback.failedPath = ''"
    >
      留在此页
    </button>
  </section>
</template>
<style scoped>
.mm-route-progress {
  position: fixed;
  inset: 0 0 auto;
  height: 3px;
  z-index: 200;
  overflow: hidden;
  background: #eed8c8;
}
.mm-route-progress span {
  display: block;
  width: 35%;
  height: 100%;
  background: var(--mm-primary);
  animation: progress 1.3s ease-in-out infinite;
}
@keyframes progress {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(390%);
  }
}
.mm-connectivity {
  padding: 10px 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  background: #fff3dc;
  color: #765014;
  font-size: 13px;
}
.mm-connectivity__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
  flex: none;
}
.mm-navigation-error {
  position: fixed;
  z-index: 120;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  width: min(640px, calc(100% - 32px));
  padding: 18px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  border: 1px solid var(--mm-border);
  border-radius: 14px;
  background: white;
  box-shadow: 0 12px 44px #24252226;
}
.mm-navigation-error div {
  flex: 1 1 260px;
}
.mm-navigation-error p {
  font-size: 13px;
  color: var(--mm-muted);
  margin-top: 6px;
}
.mm-navigation-error button {
  min-height: 44px;
  padding: 8px 14px;
  border: 0;
  border-radius: 8px;
  background: var(--mm-ink);
  color: white;
}
.mm-navigation-error .mm-navigation-error__dismiss {
  background: var(--mm-canvas);
  color: var(--mm-ink);
}
@media (prefers-reduced-motion: reduce) {
  .mm-route-progress span {
    animation: none;
    width: 100%;
  }
}
</style>
