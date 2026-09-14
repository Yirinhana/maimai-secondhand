<script setup lang="ts">
import { computed } from 'vue';
import type { Category } from '../../../shared/types';
const props = defineProps<{ categories: Category[]; selected: string }>();
const emit = defineEmits<{ select: [id: string] }>();
function contains(node: Category): boolean {
  return String(node.id) === props.selected || !!node.children?.some(contains);
}
const root = computed(() => props.categories.find(contains));
</script>
<template>
  <nav class="category-browser" aria-label="商品分类筛选">
    <div class="category-browser__heading">
      <strong>按分类逛逛</strong><span>找到你感兴趣的闲置</span>
    </div>
    <div class="category-browser__roots">
      <button
        type="button"
        :aria-pressed="!selected"
        @click="emit('select', '')"
      >
        全部分类
      </button>
      <button
        v-for="category in categories"
        :key="category.id"
        type="button"
        :aria-pressed="root?.id === category.id"
        @click="emit('select', String(category.id))"
      >
        {{ category.name }}
      </button>
    </div>
    <div
      v-if="root?.children?.length"
      class="category-browser__children"
      :aria-label="`${root.name}子分类`"
    >
      <button
        type="button"
        :aria-pressed="selected === String(root.id)"
        @click="emit('select', String(root.id))"
      >
        全部{{ root.name }}
      </button>
      <button
        v-for="child in root.children"
        :key="child.id"
        type="button"
        :aria-pressed="selected === String(child.id)"
        @click="emit('select', String(child.id))"
      >
        {{ child.name }}
      </button>
    </div>
  </nav>
</template>
<style scoped>
.category-browser {
  margin: 0 0 24px;
  background: white;
  border: 1px solid var(--mm-border);
  border-radius: 16px;
  padding: 20px 22px;
}
.category-browser__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.category-browser__heading span {
  font-size: 12px;
  color: var(--mm-muted);
}
.category-browser__roots,
.category-browser__children {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.category-browser button {
  border: 1px solid transparent;
  border-radius: 9px;
  background: #f6f5f1;
  color: var(--mm-ink);
  padding: 10px 15px;
  cursor: pointer;
  font-size: 13px;
}
.category-browser button[aria-pressed='true'] {
  background: var(--mm-primary);
  color: white;
}
.category-browser button:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: 3px;
}
.category-browser__children {
  border-top: 1px solid var(--mm-border);
  margin-top: 16px;
  padding-top: 16px;
}
.category-browser__children button {
  background: transparent;
  padding: 7px 12px;
}
.category-browser__children button[aria-pressed='true'] {
  color: var(--mm-primary);
  background: #f8eee3;
}
@media (max-width: 600px) {
  .category-browser {
    padding: 16px;
  }
  .category-browser__heading span {
    display: none;
  }
  .category-browser button {
    padding: 9px 11px;
    font-size: 12px;
  }
}
</style>
