<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { get, post } from '../../shared/api';
import { formatTime } from '../../shared/format';
import MmPagination from '../../shared/components/MmPagination.vue';
import ConversationListPage from '../messaging/ConversationListPage.vue';
const route = useRoute(),
  router = useRouter();
const tabs = [
  ['ALL', '全部通知'],
  ['CHAT', '私信'],
  ['TRADE', '交易进度'],
  ['SERVICE', '售后与客服'],
  ['DISCOVERY', '找货提醒'],
  ['SYSTEM', '平台通知'],
];
interface Entry {
  id: number;
  title: string;
  content: string;
  category: string;
  read: boolean;
  createdAt: string;
  targetPath: string | null;
}
const data = ref<{
    items: Entry[];
    total: number;
    totalPages: number;
    unread: number;
  }>({ items: [], total: 0, totalPages: 0, unread: 0 }),
  loading = ref(false),
  error = ref('');
const category = ref('ALL'),
  unreadOnly = ref(false),
  page = ref(0);
let request = 0;
function navigate(tab = category.value, p = 0) {
  router.replace({
    path: '/notifications',
    query: {
      tab,
      page: p || undefined,
      unread: unreadOnly.value ? '1' : undefined,
    },
  });
}
async function load() {
  const generation = ++request;
  if (category.value === 'CHAT') return;
  loading.value = true;
  error.value = '';
  try {
    const result = await get<typeof data.value>('/me/inbox', {
      category: category.value,
      page: page.value,
      unreadOnly: unreadOnly.value,
    });
    if (generation === request) data.value = result;
  } catch (e) {
    if (generation === request) error.value = (e as Error).message;
  } finally {
    if (generation === request) loading.value = false;
  }
}
async function read(item: Entry) {
  try {
    await post('/me/notifications/read', { ids: [item.id] });
    if (!item.read) {
      item.read = true;
      data.value.unread = Math.max(0, data.value.unread - 1);
    }
    if (item.targetPath) await router.push(item.targetPath);
  } catch (e) {
    error.value = (e as Error).message;
  }
}
async function readPage() {
  try {
    await post('/me/notifications/read', {
      ids: data.value.items.filter((i) => !i.read).map((i) => i.id),
    });
    await load();
  } catch (e) {
    error.value = (e as Error).message;
  }
}
watch(
  () => route.query,
  () => {
    category.value = tabs.some((t) => t[0] === route.query.tab)
      ? String(route.query.tab)
      : 'ALL';
    page.value = Math.max(0, Number(route.query.page) || 0);
    unreadOnly.value = route.query.unread === '1';
    void load();
  },
  { immediate: true },
);
</script>
<template>
  <section class="mm-page notice-center">
    <header>
      <p class="mm-eyebrow">我的消息</p>
      <h1>每一步进展，都有回音</h1>
      <p class="mm-muted">私信与通知分开管理，订单和客服进度可以直接查看。</p>
    </header>
    <nav class="notice-tabs" aria-label="消息分类">
      <button
        v-for="t in tabs"
        :key="t[0]"
        :aria-pressed="category === t[0]"
        @click="navigate(t[0])"
      >
        {{ t[1] }}
      </button>
    </nav>
    <ConversationListPage v-if="category === 'CHAT'" />
    <template v-else>
      <div class="notice-tools">
        <label
          ><input
            v-model="unreadOnly"
            type="checkbox"
            @change="navigate()"
          />只看未读</label
        ><span>{{ data.unread }} 条未读通知</span
        ><button
          :disabled="loading || !data.items.some((i) => !i.read)"
          @click="readPage"
        >
          本页标为已读
        </button>
      </div>
      <p v-if="error" class="mm-error" role="alert">
        {{ error }} <button @click="load">重试</button>
      </p>
      <p v-else-if="loading" role="status">正在读取消息…</p>
      <p v-else-if="!data.items.length" class="mm-panel">
        {{
          unreadOnly
            ? '没有未读通知，事情都处理得很有条理。'
            : '这里还没有通知，有新进展时会及时告诉你。'
        }}
      </p>
      <ul v-else class="notice-list">
        <li
          v-for="item in data.items"
          :key="item.id"
          :class="{ 'is-new': !item.read }"
        >
          <div>
            <strong>{{ item.title }}</strong
            ><span
              v-if="!item.read"
              class="notice-dot"
              aria-label="未读"
            /><time>{{ formatTime(item.createdAt) }}</time>
          </div>
          <p>{{ item.content }}</p>
          <button @click="read(item)">
            {{
              item.targetPath ? '查看详情 →' : item.read ? '已读' : '标为已读'
            }}
          </button>
        </li>
      </ul>
      <MmPagination
        :page="page"
        :total-pages="data.totalPages"
        @change="navigate(category, $event)"
      />
    </template>
  </section>
</template>
<style scoped>
.notice-center {
  display: grid;
  gap: 20px;
}
.notice-tabs,
.notice-tools {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}
.notice-tabs button {
  padding: 10px 16px;
  border: 1px solid var(--mm-border);
  border-radius: 24px;
  background: var(--mm-surface, #fff);
  color: var(--mm-ink);
}
.notice-tabs button[aria-pressed='true'] {
  background: var(--mm-primary);
  color: white;
  border-color: transparent;
}
.notice-tools {
  font-size: 14px;
  justify-content: space-between;
}
.notice-tools label {
  display: flex;
  align-items: center;
  gap: 8px;
}
.notice-tools button,
.notice-list button {
  color: var(--mm-primary);
  background: none;
  border: 0;
  padding: 10px 0;
  font: inherit;
  cursor: pointer;
}
.notice-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}
.notice-list li {
  border: 1px solid var(--mm-border);
  padding: 22px;
  border-radius: 12px;
  background: white;
  overflow-wrap: anywhere;
}
.notice-list li.is-new {
  border-left: 4px solid var(--mm-primary);
}
.notice-list li > div {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.notice-list time {
  font-size: 12px;
  color: var(--mm-muted);
  margin-left: auto;
}
.notice-list p {
  line-height: 1.75;
  white-space: pre-wrap;
}
.notice-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--mm-primary);
}
</style>
