<template>
  <div class="mm-placeholder">
    <img src="/brand/maimai-icon.svg" alt="" width="48" height="48" />
    <h1 class="mm-placeholder__name">{{ moduleName }}</h1>
    <p class="mm-placeholder__note">功能待实现，后续迭代将补全本页面。</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{ moduleName: string }>()
</script>

<style scoped>
.mm-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mm-space-3);
  padding: var(--mm-space-6) var(--mm-space-4);
  text-align: center;
}

.mm-placeholder__name {
  font-size: var(--mm-font-xl);
  color: var(--mm-ink);
}

.mm-placeholder__note {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}
</style>
