<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue';
import { get } from '../../shared/api';
interface Task {
  kind: string;
  title: string;
  targetPath: string;
  deadline: string | null;
}
const data = ref<{ counts: Record<string, number>; items: Task[] }>(),
  error = ref(''),
  loading = ref(false),
  now = ref(Date.now());
const groups = [
  ['SHIP', '待发货', '/seller/orders'],
  ['BARGAIN', '待回复议价', '/seller/bargains'],
  ['AFTERSALE', '待处理售后', '/seller/aftersales'],
  ['MESSAGE', '未读私信', '/messages'],
];
let timer: ReturnType<typeof setInterval>;
async function load() {
  loading.value = true;
  error.value = '';
  try {
    data.value = await get('/seller/tasks');
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
function due(deadline: string) {
  const minutes = Math.ceil((Date.parse(deadline) - now.value) / 60000);
  return minutes <= 0
    ? '已超过处理时限'
    : minutes < 60
      ? `剩余 ${minutes} 分钟`
      : `剩余 ${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`;
}
onMounted(() => {
  void load();
  timer = setInterval(() => {
    now.value = Date.now();
    if (document.visibilityState === 'visible') void load();
  }, 60000);
});
onBeforeUnmount(() => clearInterval(timer));
</script>
<template>
  <section class="seller-tasks mm-panel">
    <header>
      <div>
        <h2>今天先处理这些</h2>
        <p class="mm-muted">
          按实际订单与沟通记录整理，优先查看临近期限的事项。
        </p>
      </div>
      <button :disabled="loading" @click="load">刷新待办</button>
    </header>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-if="!data && loading" role="status">正在整理待办…</p>
    <template v-if="data"
      ><div class="task-counts">
        <RouterLink v-for="g in groups" :key="g[0]" :to="g[2]!"
          ><span>{{ g[1] }}</span
          ><strong>{{ data.counts[g[0]!] ?? 0 }}</strong></RouterLink
        >
      </div>
      <ul>
        <li v-for="(t, i) in data.items" :key="i">
          <RouterLink :to="t.targetPath"
            ><span>{{ t.title }}</span
            ><small
              v-if="t.deadline"
              :class="{ 'is-overdue': Date.parse(t.deadline) < now }"
              >{{ due(t.deadline) }}</small
            ><span aria-hidden="true">→</span></RouterLink
          >
        </li>
      </ul>
      <p v-if="!data.items.length" class="mm-muted">
        暂时没有待办，可以去整理商品或查看店铺。
      </p>
      <p v-else class="mm-muted">
        各类展示最早的5项，点击上方数量可查看全部。
      </p></template
    >
  </section>
</template>
<style scoped>
.seller-tasks header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.seller-tasks button {
  flex-shrink: 0;
}
.task-counts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 20px 0;
}
.task-counts a {
  padding: 16px;
  background: var(--mm-bg, #faf9f6);
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
  display: grid;
  gap: 8px;
}
.task-counts strong {
  font-size: 26px;
}
.seller-tasks ul {
  list-style: none;
  padding: 0;
  margin: 0;
}
.seller-tasks li a {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 16px 0;
  color: inherit;
  text-decoration: none;
  border-bottom: 1px solid var(--mm-border);
}
.seller-tasks li a > span:first-child {
  flex: 1;
  min-width: 180px;
  overflow-wrap: anywhere;
}
.seller-tasks small {
  color: var(--mm-muted);
}
.seller-tasks .is-overdue {
  color: #a43f26;
}
@media (max-width: 600px) {
  .task-counts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .seller-tasks header {
    align-items: flex-start;
  }
}
</style>
