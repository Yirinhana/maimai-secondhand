<template>
  <nav v-if="totalPages > 1" class="mm-pagination" aria-label="分页">
    <MmButton
      variant="ghost"
      :disabled="page <= 0"
      @click="emit('change', page - 1)"
    >
      上一页
    </MmButton>
    <div class="mm-pagination__numbers">
      <button
        v-for="p in visiblePages"
        :key="p"
        type="button"
        :aria-label="`第 ${p + 1} 页`"
        :aria-current="p === page ? 'page' : undefined"
        @click="emit('change', p)"
      >
        {{ p + 1 }}
      </button>
    </div>
    <span class="mm-pagination__info" aria-live="polite"
      >第 {{ page + 1 }} / {{ totalPages }} 页</span
    >
    <MmButton
      variant="ghost"
      :disabled="page >= totalPages - 1"
      @click="emit('change', page + 1)"
    >
      下一页
    </MmButton>
  </nav>
</template>

<script setup lang="ts">
import MmButton from './MmButton.vue';
import { computed } from 'vue';

const props = defineProps<{ page: number; totalPages: number }>();
const visiblePages = computed(() => {
  const count = Math.min(5, props.totalPages),
    start = Math.max(
      0,
      Math.min(props.page - 2, props.totalPages - count),
    );
  return Array.from({ length: count }, (_, i) => start + i);
});
const emit = defineEmits<{ change: [page: number] }>();
</script>

<style scoped>
.mm-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--mm-space-4);
  padding: var(--mm-space-4) 0;
}

.mm-pagination__info {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}
.mm-pagination__numbers {
  display: flex;
  gap: 6px;
}
.mm-pagination__numbers button {
  width: 44px;
  min-height: 44px;
  border: 1px solid var(--mm-border);
  background: white;
  color: var(--mm-ink);
  border-radius: 8px;
  font-size: 14px;
}
.mm-pagination__numbers button[aria-current] {
  background: var(--mm-ink);
  color: white;
  border-color: var(--mm-ink);
}
@media (min-width: 701px) {
  .mm-pagination__info {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
  }
}
@media (max-width: 700px) {
  .mm-pagination__numbers {
    display: none;
  }
  .mm-pagination {
    gap: 12px;
  }
}
</style>
