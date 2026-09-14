<template>
  <nav v-if="totalPages > 1" class="mm-pagination" aria-label="分页">
    <MmButton variant="ghost" :disabled="page <= 0" @click="emit('change', page - 1)">
      上一页
    </MmButton>
    <span class="mm-pagination__info">第 {{ page + 1 }} / {{ totalPages }} 页</span>
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
import MmButton from './MmButton.vue'

defineProps<{ page: number; totalPages: number }>()
const emit = defineEmits<{ change: [page: number] }>()
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
</style>
