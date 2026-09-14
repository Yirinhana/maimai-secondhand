<template>
  <div class="mm-empty">
    <div class="mm-empty__icon" aria-hidden="true"><MmIcon :name="icon" /></div>
    <p class="mm-empty__title">{{ title }}</p>
    <p v-if="description" class="mm-empty__desc">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
import MmIcon from './MmIcon.vue';
withDefaults(
  defineProps<{ title?: string; description?: string; icon?: string }>(),
  { title: '这里还没有内容', description: '', icon: 'box' },
);
</script>

<style scoped>
.mm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mm-space-2);
  padding: 44px 20px;
  text-align: center;
  background: white;
  border: 1px dashed var(--mm-zone-border, var(--mm-border));
  border-radius: 12px;
}

.mm-empty__icon {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border-radius: 22px;
  color: var(--mm-zone-accent, var(--mm-muted));
  background: var(--mm-zone-soft, var(--mm-canvas));
  margin-bottom: 9px;
}
.mm-empty__icon .mm-icon {
  width: 34px;
  height: 34px;
  stroke-width: 1.25;
}
.mm-empty :deep(a),
.mm-empty :deep(button) {
  margin-top: 8px;
}

.mm-empty__title {
  font-size: var(--mm-font-l);
  font-weight: 600;
  color: var(--mm-ink);
}

.mm-empty__desc {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  max-width: 380px;
  line-height: 1.8;
}
</style>
