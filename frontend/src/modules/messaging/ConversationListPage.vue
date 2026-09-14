<template>
  <section class="mm-inbox">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">MESSAGES</p>
        <h1>私信</h1>
        <p>关于好物的沟通，都留在这里。</p>
      </div>
      <span
        class="mm-inbox__connection"
        :class="{ 'is-ready': status === 'ready' }"
        role="status"
        >{{ status === 'ready' ? '实时消息已连接' : statusText }}</span
      >
    </header>
    <div class="mm-inbox__layout">
      <section class="mm-inbox__mailbox" :aria-busy="loading">
        <div class="mm-inbox__label">
          <h2>交易私信</h2>
          <span>{{
            overview
              ? `${overview.conversations} 个会话 · ${overview.unreadMessages} 条未读`
              : '正在读取收件箱'
          }}</span>
        </div>
        <div class="mm-inbox__tools">
          <nav aria-label="私信筛选" class="mm-inbox__tabs">
            <button
              type="button"
              :aria-pressed="!unreadOnly"
              :class="{ 'is-active': !unreadOnly }"
              @click="filter(false)"
            >
              全部会话 <span v-if="overview">{{ overview.conversations }}</span>
            </button>
            <button
              type="button"
              :aria-pressed="unreadOnly"
              :class="{ 'is-active': unreadOnly }"
              @click="filter(true)"
            >
              未读会话
              <span v-if="overview">{{ overview.unreadConversations }}</span>
            </button>
          </nav>
          <form
            class="mm-inbox__search"
            role="search"
            aria-label="搜索私信会话"
            @submit.prevent="search"
          >
            <label
              ><MmIcon name="search" /><span class="mm-visually-hidden"
                >昵称或关联商品</span
              ><input
                v-model="draftKeyword"
                type="search"
                maxlength="100"
                placeholder="按昵称或关联商品查找" /></label
            ><MmButton type="submit">搜索</MmButton>
          </form>
          <div v-if="keyword || unreadOnly" class="mm-inbox__applied">
            <span
              >{{ unreadOnly ? '仅未读会话' : '全部会话'
              }}{{ keyword ? ` · 搜索「${keyword}」` : '' }}</span
            ><button type="button" @click="resetFilters">清除筛选</button>
          </div>
        </div>
        <div v-if="error" class="mm-inbox__error" role="alert">
          <p>{{ error }}</p>
          <MmButton variant="ghost" @click="load()">重新加载</MmButton>
        </div>
        <p v-else-if="loading" class="mm-inbox__hint" role="status">
          正在查找会话…
        </p>
        <EmptyState
          v-else-if="!conversations.length"
          icon="message"
          :title="keyword || unreadOnly ? '没有符合条件的会话' : '还没有会话'"
          :description="
            keyword || unreadOnly
              ? '换个关键词，或查看全部会话。'
              : '在商品详情或订单中联系对方，消息会归到这里。'
          "
        >
          <MmButton
            v-if="keyword || unreadOnly"
            variant="ghost"
            @click="resetFilters"
            >查看全部会话</MmButton
          ><RouterLink v-else to="/search">去发现好物 →</RouterLink>
        </EmptyState>
        <ul v-else class="mm-inbox__list">
          <li v-for="c in conversations" :key="c.id">
            <RouterLink
              :to="{ path: `/messages/${c.id}`, query: route.query }"
              class="mm-inbox__item"
              :class="{ 'is-unread': c.unread > 0 }"
            >
              <UserAvatar
                :src="c.otherAvatarUrl"
                :nickname="c.otherNickname"
                :size="46"
              />
              <div class="mm-inbox__content">
                <div class="mm-inbox__row">
                  <strong>{{ c.otherNickname }}</strong
                  ><time>{{ formatTime(c.updatedAt) }}</time>
                </div>
                <p v-if="c.productId" class="mm-inbox__product">
                  <MmIcon name="box" />{{
                    c.productTitle || `关联商品 #${c.productId}`
                  }}
                </p>
                <div class="mm-inbox__row">
                  <span class="mm-inbox__preview">{{ preview(c) }}</span
                  ><span
                    v-if="c.unread > 0"
                    class="mm-inbox__unread"
                    :aria-label="`${c.unread} 条未读`"
                    >{{ c.unread > 99 ? '99+' : c.unread }}</span
                  >
                </div>
              </div>
            </RouterLink>
          </li>
        </ul>
        <nav
          v-if="page > 0 || conversations.length === size"
          class="mm-inbox__pager"
          aria-label="会话分页"
        >
          <MmButton
            variant="ghost"
            :disabled="page === 0 || loading"
            @click="navigate(page - 1)"
            >上一页</MmButton
          ><span>第 {{ page + 1 }} 页</span
          ><MmButton
            variant="ghost"
            :disabled="conversations.length < size || loading"
            @click="navigate(page + 1)"
            >下一页</MmButton
          >
        </nav>
      </section>
      <aside class="mm-inbox__guide">
        <MmIcon name="message" />
        <h2>从一声招呼开始</h2>
        <p>
          询问成色、确认交付方式，或聊聊你感兴趣的那件闲置。商品详情和订单中都可以联系对方。
        </p>
        <RouterLink to="/search">发现想聊的好物 →</RouterLink>
        <div>
          <MmIcon name="shield" /><strong>保留沟通记录</strong>
          <p>涉及订单的问题可随时联系平台客服，历史消息会继续保留。</p>
          <RouterLink to="/support">帮助与客服 →</RouterLink>
        </div>
      </aside>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import MmButton from '../../shared/components/MmButton.vue';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import MmIcon from '../../shared/components/MmIcon.vue';
import EmptyState from '../../shared/components/EmptyState.vue';
import { get, type ApiError } from '../../shared/api';
import { formatTime } from '../../shared/format';
import type { ConversationSummary } from '../../shared/types';
import { useAuthStore } from '../../shared/stores/auth';
import { useMessageSocket } from './socket';
const router = useRouter(),
  route = useRoute(),
  auth = useAuthStore();
const conversations = ref<ConversationSummary[]>([]);
const overview = ref<{
  conversations: number;
  unreadConversations: number;
  unreadMessages: number;
} | null>(null);
const size = 20,
  loading = ref(false),
  error = ref(''),
  draftKeyword = ref('');
const keyword = computed(() =>
  typeof route.query.keyword === 'string'
    ? route.query.keyword.trim().slice(0, 100)
    : '',
);
const unreadOnly = computed(() => route.query.unread === '1');
const page = computed(() => {
  const value = Number(route.query.page ?? 0);
  return Number.isInteger(value) && value >= 0 && value <= 10000 ? value : 0;
});
let sequence = 0,
  disposed = false;
async function load(quiet = false) {
  const requested = ++sequence,
    userId = auth.me?.id;
  if (!userId) return;
  if (!quiet) loading.value = true;
  error.value = '';
  try {
    const [list, counts] = await Promise.all([
      get<ConversationSummary[]>('/messages/conversations', {
        page: page.value,
        size,
        keyword: keyword.value,
        unreadOnly: unreadOnly.value,
      }),
      get<NonNullable<typeof overview.value>>(
        '/messages/conversations/overview',
      ),
    ]);
    if (disposed || requested !== sequence || userId !== auth.me?.id) return;
    conversations.value = list;
    overview.value = counts;
  } catch (e) {
    if (!disposed && requested === sequence && userId === auth.me?.id)
      error.value = (e as ApiError).message || '会话列表加载失败';
  } finally {
    if (!disposed && requested === sequence) loading.value = false;
  }
}
function navigate(
  nextPage = 0,
  nextKeyword = keyword.value,
  nextUnread = unreadOnly.value,
) {
  const target = {
    path: '/messages',
    query: {
      keyword: nextKeyword || undefined,
      unread: nextUnread ? '1' : undefined,
      page: nextPage ? String(nextPage) : undefined,
    },
  };
  if (router.resolve(target).fullPath === route.fullPath) void load();
  else void router.push(target);
}
function search() {
  navigate(0, draftKeyword.value.trim(), unreadOnly.value);
}
function filter(unread: boolean) {
  navigate(0, keyword.value, unread);
}
function resetFilters() {
  draftKeyword.value = '';
  navigate(0, '', false);
}
function preview(c: ConversationSummary) {
  return c.lastMessage || '尚未发送消息，打个招呼吧';
}
const { status, statusText } = useMessageSocket({
  onReady: () => void load(true),
  onChanged: () => void load(true),
});
watch(
  () => [auth.me?.id, keyword.value, unreadOnly.value, page.value] as const,
  () => {
    ++sequence;
    conversations.value = [];
    overview.value = null;
    draftKeyword.value = keyword.value;
    error.value = '';
    if (auth.me) void load();
  },
  { immediate: true, flush: 'sync' },
);
onBeforeUnmount(() => {
  disposed = true;
  ++sequence;
});
</script>
<style scoped>
.mm-inbox {
  max-width: 1180px;
  margin: auto;
  padding: 32px 28px 56px;
}
.mm-inbox__connection {
  font-size: 11px;
  color: var(--mm-primary);
  border: 1px solid var(--mm-zone-border);
  background: var(--mm-zone-soft);
  padding: 5px 10px;
  border-radius: 20px;
}
.mm-inbox__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 275px;
  gap: 30px;
  margin-top: 27px;
  align-items: start;
}
.mm-inbox__mailbox {
  background: white;
  border: 1px solid var(--mm-zone-border);
  border-radius: 8px;
  min-width: 0;
  min-height: 360px;
}
.mm-inbox__label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--mm-zone-soft);
  padding: 18px 22px;
  border-bottom: 1px solid var(--mm-zone-border);
}
.mm-inbox__label h2 {
  font-size: 15px;
}
.mm-inbox__label > span {
  font-size: 11px;
  color: var(--mm-muted);
}
.mm-inbox__item {
  display: flex;
  align-items: center;
  gap: 15px;
  width: 100%;
  border: 0;
  border-bottom: 1px solid #e8ebe5;
  background: white;
  text-align: left;
  padding: 22px;
}
.mm-inbox__item:hover {
  background: var(--mm-zone-soft);
}
.mm-inbox__content {
  flex: 1;
  min-width: 0;
}
.mm-inbox__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.mm-inbox__row strong {
  font-size: 14px;
  overflow-wrap: anywhere;
}
.mm-inbox__row time {
  font-size: 10px;
  color: var(--mm-muted);
  white-space: nowrap;
}
.mm-inbox__preview {
  font-size: 12px;
  color: var(--mm-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 8px;
}
.mm-inbox__unread {
  color: white;
  background: var(--mm-primary);
  border-radius: 20px;
  min-width: 19px;
  text-align: center;
  padding: 1px 5px;
  font-size: 10px;
}
.mm-inbox__guide {
  padding: 25px 8px 0;
  color: #2b5454;
}
.mm-inbox__guide > .mm-icon {
  width: 33px;
  height: 33px;
}
.mm-inbox__guide h2 {
  font-size: 19px;
  margin: 18px 0 13px;
}
.mm-inbox__guide p {
  font-size: 12px;
  line-height: 1.95;
  color: #627a76;
  margin: 10px 0 18px;
}
.mm-inbox__guide a {
  color: var(--mm-primary);
  font-size: 12px;
  font-weight: 650;
}
.mm-inbox__guide > div {
  margin-top: 34px;
  padding-top: 25px;
  border-top: 1px solid var(--mm-zone-border);
}
.mm-inbox__guide > div > .mm-icon {
  width: 18px;
  height: 18px;
  margin-right: 8px;
}
.mm-inbox__guide strong {
  font-size: 13px;
}
.mm-inbox__pager {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding: 17px;
}
.mm-inbox__hint {
  padding: 25px;
  color: var(--mm-muted);
  font-size: 13px;
}
.mm-inbox__mailbox > .mm-error {
  padding: 20px;
}
@media (max-width: 760px) {
  .mm-inbox {
    padding: 23px 18px 36px;
  }
  .mm-inbox__layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 15px;
    margin-top: 20px;
  }
  .mm-inbox__guide {
    padding: 15px 5px;
  }
  .mm-inbox__guide > div {
    margin-top: 20px;
    padding-top: 17px;
  }
  .mm-inbox__item {
    padding: 17px 14px;
    gap: 12px;
  }
  .mm-inbox__row {
    flex-wrap: wrap;
    gap: 4px;
  }
  .mm-inbox__row time {
    font-size: 10px;
  }
  .mm-inbox__item :deep(.mm-avatar) {
    width: 36px !important;
    height: 36px !important;
  }
}

.mm-inbox__item {
  color: var(--mm-ink);
  text-decoration: none;
  border-left: 3px solid transparent;
}
.mm-inbox__item.is-unread {
  background: #f0f8f6;
  border-left-color: var(--mm-primary);
}
.mm-inbox__mailbox {
  overflow: hidden;
  border-radius: 13px;
}
.mm-inbox__tools {
  padding: 18px 22px;
  border-bottom: 1px solid var(--mm-zone-border);
}
.mm-inbox__tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.mm-inbox__tabs button {
  border: 0;
  border-radius: 7px;
  background: #f3f6f5;
  color: var(--mm-muted);
  padding: 10px 15px;
  font: inherit;
  font-size: 13px;
  min-height: 42px;
}
.mm-inbox__tabs button.is-active {
  background: var(--mm-primary);
  color: white;
  font-weight: 650;
}
.mm-inbox__tabs span {
  margin-left: 6px;
  font-variant-numeric: tabular-nums;
}
.mm-inbox__search {
  display: flex;
  gap: 10px;
}
.mm-inbox__search label {
  display: flex;
  gap: 8px;
  align-items: center;
  flex: 1;
  min-width: 0;
  border: 1px solid #bbcdca;
  border-radius: 8px;
  padding: 0 12px;
}
.mm-inbox__search .mm-icon {
  width: 17px;
  height: 17px;
  color: #66817d;
  flex: none;
}
.mm-inbox__search input {
  border: 0;
  background: transparent;
  outline: 0;
  min-width: 0;
  width: 100%;
  min-height: 44px;
  font-size: 14px;
}
.mm-inbox__search label:focus-within {
  outline: 2px solid var(--mm-primary);
  outline-offset: 2px;
}
.mm-inbox__applied {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  padding-top: 12px;
  color: var(--mm-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.mm-inbox__applied button {
  background: none;
  border: 0;
  color: var(--mm-primary);
  font: inherit;
}
.mm-inbox__product {
  display: flex;
  align-items: center;
  gap: 5px;
  color: #547b75;
  font-size: 11px;
  margin-top: 6px;
  overflow-wrap: anywhere;
}
.mm-inbox__product .mm-icon {
  width: 13px;
  height: 13px;
  flex: none;
}
.mm-inbox__unread {
  flex: none;
}
.mm-inbox__guide {
  padding: 24px;
  border: 1px solid var(--mm-zone-border);
  border-radius: 13px;
  background: #f8fcfb;
}
.mm-inbox__pager {
  align-items: center;
  font-size: 12px;
}
.mm-inbox__error {
  padding: 22px;
  color: var(--mm-danger);
  font-size: 13px;
}
.mm-inbox__error .mm-button {
  margin-top: 12px;
}
.mm-inbox__connection:not(.is-ready) {
  color: #856025;
  background: #fcf7ea;
  border-color: #e3d4b7;
}
.mm-inbox__mailbox :deep(.mm-empty) {
  border: 0;
}
@media (max-width: 760px) {
  .mm-inbox__tools {
    padding: 16px 14px;
  }
  .mm-inbox__label {
    flex-wrap: wrap;
    gap: 8px;
  }
  .mm-inbox__tabs {
    gap: 5px;
  }
  .mm-inbox__tabs button {
    padding: 10px 12px;
    font-size: 12px;
  }
  .mm-inbox__search {
    gap: 7px;
  }
  .mm-inbox__search input {
    font-size: 16px;
  }
  .mm-inbox__search label {
    padding: 0 8px;
  }
  .mm-inbox__guide {
    padding: 20px;
  }
}
</style>
