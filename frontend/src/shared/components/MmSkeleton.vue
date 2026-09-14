<template>
  <div
    class="mm-skeleton"
    :class="`mm-skeleton--${kind}`"
    role="status"
    aria-live="polite"
  >
    <span class="mm-visually-hidden">{{ label }}</span>
    <div
      v-for="index in safeCount"
      :key="index"
      class="mm-skeleton__item"
      aria-hidden="true"
    >
      <div class="mm-skeleton__image" />
      <div class="mm-skeleton__text">
        <span class="mm-skeleton__line mm-skeleton__line--title" />
        <span class="mm-skeleton__line" />
        <span class="mm-skeleton__line mm-skeleton__line--short" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
const props = withDefaults(
  defineProps<{
    kind?: 'cards' | 'detail' | 'rows';
    count?: number;
    label?: string;
  }>(),
  { kind: 'cards', count: 6, label: '加载中' },
);
const safeCount = computed(() =>
  Math.max(1, Math.min(12, Math.trunc(props.count) || 1)),
);
</script>

<style scoped>
.mm-skeleton {
  display: grid;
  gap: 24px;
  width: 100%;
}
.mm-skeleton--cards {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}
.mm-skeleton__item {
  min-width: 0;
}
.mm-skeleton__image,
.mm-skeleton__line {
  background: #e9e9e2;
  animation: mm-skeleton-pulse 1.5s ease-in-out infinite alternate;
}
.mm-skeleton__image {
  aspect-ratio: 1;
  border-radius: 12px;
}
.mm-skeleton__text {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 18px;
}
.mm-skeleton__line {
  display: block;
  height: 11px;
  border-radius: 4px;
  width: 70%;
}
.mm-skeleton__line--title {
  width: 92%;
  height: 16px;
}
.mm-skeleton__line--short {
  width: 38%;
}
.mm-skeleton--rows .mm-skeleton__item {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px;
  background: white;
  border: 1px solid var(--mm-border);
  border-radius: 10px;
}
.mm-skeleton--rows .mm-skeleton__image {
  width: 72px;
  flex-shrink: 0;
}
.mm-skeleton--rows .mm-skeleton__text {
  flex: 1;
  padding: 0;
}
.mm-skeleton--detail {
  grid-template-columns: 1fr;
}
.mm-skeleton--detail .mm-skeleton__item {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
}
.mm-skeleton--detail .mm-skeleton__text {
  padding: 24px 0;
  gap: 26px;
}
.mm-skeleton--detail .mm-skeleton__line--title {
  height: 30px;
}
@keyframes mm-skeleton-pulse {
  to {
    opacity: 0.48;
  }
}
@media (max-width: 600px) {
  .mm-skeleton--cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 20px 14px;
  }
  .mm-skeleton--detail .mm-skeleton__item {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .mm-skeleton__image,
  .mm-skeleton__line {
    animation: none;
  }
}
</style>
