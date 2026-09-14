<template>
  <div class="mm-empty">
    <img class="mm-empty__icon" src="/brand/maimai-icon.svg" alt="" width="64" height="64" />
    <p class="mm-empty__title">{{ title }}</p>
    <p v-if="description" class="mm-empty__desc">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{ title?: string; description?: string }>(),
  { title: '这里还没有内容', description: '' },
)
</script>

<style scoped>
.mm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mm-space-2);
  padding: var(--mm-space-6) var(--mm-space-4);
  text-align: center;
}

.mm-empty__icon {
  opacity: 0.6;
}

.mm-empty__title {
  font-size: var(--mm-font-l);
  font-weight: 600;
  color: var(--mm-ink);
}

.mm-empty__desc {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}
</style>
