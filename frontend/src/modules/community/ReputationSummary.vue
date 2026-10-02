<script setup lang="ts">
import { ref, watch } from 'vue';
import { get } from '../../shared/api';
const props = defineProps<{ userId: number }>();
interface Summary {
  level: string;
  scopes: {
    source: string;
    label: string;
    ratings: number;
    averageRating: number | null;
  }[];
}
const data = ref<Summary>(),
  failed = ref(false);
let generation = 0;
watch(
  () => props.userId,
  async (id) => {
    const current = ++generation;
    data.value = undefined;
    failed.value = false;
    try {
      const result = await get<Summary>(`/community/users/${id}/reputation`);
      if (current === generation) data.value = result;
    } catch {
      if (current === generation) failed.value = true;
    }
  },
  { immediate: true },
);
</script>
<template>
  <aside class="reputation-summary">
    <RouterLink :to="`/sellers/${userId}`">卖家信誉与成交评价 →</RouterLink>
    <p v-if="failed">暂时无法读取信誉，请稍后查看卖家主页。</p>
    <template v-else-if="data"
      ><span>{{ data.level }}</span>
      <p v-for="s in data.scopes.filter((s) => s.ratings > 0)" :key="s.source">
        {{ s.label }} · {{ s.ratings }} 条评价 ·
        {{ s.averageRating?.toFixed(2) ?? '—' }} / 5
      </p>
      <p v-if="!data.scopes.some((s) => s.ratings > 0)">
        暂无有效交易评价，建议先沟通成色与交付细节。
      </p></template
    >
  </aside>
</template>
<style scoped>
.reputation-summary {
  padding: 14px;
  margin-top: 16px;
  border: 1px solid var(--mm-border);
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.65;
}
.reputation-summary > a {
  font-weight: 600;
}
.reputation-summary > span {
  display: block;
  color: var(--mm-muted);
  margin-top: 6px;
}
.reputation-summary p {
  margin: 6px 0 0;
  color: var(--mm-muted);
}
</style>
