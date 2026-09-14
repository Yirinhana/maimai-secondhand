<template>
  <span class="mm-tag" :class="`mm-tag--${tone}`">
    <span class="mm-tag__dot" aria-hidden="true"></span>
    {{ text }}
  </span>
</template>

<script setup lang="ts">
/**
 * 状态标签：文字 + 色点同时表达状态，不只靠颜色区分（品牌规范要求）。
 */
withDefaults(
  defineProps<{
    text: string;
    tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';
  }>(),
  { tone: 'neutral' },
);
</script>

<style scoped>
.mm-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--mm-space-1);
  padding: 3px 10px;
  border-radius: 999px;
  font-size: var(--mm-font-s);
  font-weight: 600;
  border: 1px solid color-mix(in srgb, currentColor 22%, transparent);
  white-space: nowrap;
}

.mm-tag__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: currentColor;
  flex-shrink: 0;
}

.mm-tag--primary {
  color: var(--mm-primary);
  background-color: var(--mm-canvas);
}
.mm-tag--success {
  color: var(--mm-success);
  background-color: #edf7f1;
}
.mm-tag--warning {
  color: var(--mm-warning);
  background-color: #fbf2e4;
}
.mm-tag--danger {
  color: var(--mm-danger);
  background-color: #fbeef0;
}
.mm-tag--info {
  color: var(--mm-info);
  background-color: #edf3fa;
}
.mm-tag--neutral {
  color: var(--mm-muted);
  background-color: var(--mm-canvas);
}
</style>
