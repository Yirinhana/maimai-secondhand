<template>
  <div class="mm-chat">
    <MmCard title="私信会话" class="mm-chat__window">
      <template #extra>
        <RouterLink to="/messages" class="mm-chat__back"
          >← 返回会话列表</RouterLink
        >
      </template>

      <div v-if="otherUser" class="mm-actions">
        <strong>{{ otherUser.otherNickname }}</strong
        ><MmButton
          variant="ghost"
          :disabled="blockBusy || !blockState"
          @click="toggleBlock"
          >{{ blockState?.blockedByMe ? '取消屏蔽' : '屏蔽对方' }}</MmButton
        ><RouterLink :to="reportLink">举报并联系人工客服</RouterLink
        ><MmButton
          variant="ghost"
          :disabled="blockBusy"
          @click="loadBlockState()"
          >刷新屏蔽状态</MmButton
        >
      </div>
      <p v-if="blockState?.blockedEitherDirection" class="mm-notice">
        当前双方不能发送新消息或图片。历史记录仍可查看，订单通知不受影响。
      </p>
      <p v-if="blockError" class="mm-chat__error" role="alert">
        {{ blockError }}
      </p>

      <p v-if="statusText" class="mm-chat__conn" role="status">
        {{ statusText }}
      </p>
      <p v-if="error" class="mm-chat__error" role="alert">{{ error }}</p>

      <div ref="listEl" class="mm-chat__list" @scroll.passive="markRead">
        <button
          v-if="hasMore"
          type="button"
          class="mm-chat__more"
          :disabled="loadingMore"
          @click="loadMore"
        >
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </button>

        <p v-if="loading" class="mm-chat__hint">加载中…</p>
        <EmptyState
          v-else-if="!messages.length"
          title="还没有消息"
          description="打个招呼吧"
        />

        <div
          v-for="m in messages"
          :key="m.id"
          :data-message-id="m.id"
          class="mm-chat__row"
          :class="{ 'is-mine': m.senderId === myId }"
        >
          <div class="mm-chat__bubble">
            <img
              v-if="m.attachmentUrl"
              :src="m.attachmentUrl"
              class="mm-chat__image"
              alt="图片消息"
              loading="lazy"
            />
            <p v-if="m.body" class="mm-chat__text">{{ m.body }}</p>
            <time class="mm-chat__time">{{ formatTime(m.createdAt) }}</time>
          </div>
        </div>
      </div>

      <p v-if="sendError" class="mm-chat__error" role="alert">
        {{ sendError }}
      </p>
      <p v-if="pendingText" class="mm-chat__hint">
        重试会发送同一条消息。若要改写，请先刷新确认是否已送达。<button
          type="button"
          @click="refreshLatest"
        >
          刷新确认
        </button>
      </p>
      <p v-if="imageError" class="mm-chat__error" role="alert">
        {{ imageError }}
        <button
          v-if="pendingImage"
          type="button"
          class="mm-chat__retry"
          @click="retryImageSend"
        >
          重试
        </button>
      </p>

      <form class="mm-chat__composer" @submit.prevent="sendText">
        <input
          v-model="draft"
          :readonly="pendingText !== null || blockState?.blockedEitherDirection"
          maxlength="2000"
          placeholder="输入消息（最多 2000 字符）"
          aria-label="消息内容"
        />
        <span v-if="draft.length > 1800" class="mm-chat__count"
          >{{ draft.length }}/2000</span
        >
        <input
          ref="imageInput"
          type="file"
          accept="image/jpeg,image/png"
          hidden
          @change="onImagePicked"
        />
        <MmButton
          variant="ghost"
          :disabled="
            uploadingImage ||
            pendingImage !== null ||
            blockState?.blockedEitherDirection
          "
          @click="pickImage"
        >
          {{ uploadingImage ? '上传中…' : '图片' }}
        </MmButton>
        <MmButton
          type="submit"
          :loading="sending"
          :disabled="!draft.trim() || blockState?.blockedEitherDirection"
          >发送</MmButton
        >
      </form>
    </MmCard>
  </div>
</template>

<script setup lang="ts">
import {
  computed,
  nextTick,
  onMounted,
  onBeforeUnmount,
  ref,
  watch,
} from 'vue';
import { useRoute } from 'vue-router';
import MmButton from '../../shared/components/MmButton.vue';
import MmCard from '../../shared/components/MmCard.vue';
import EmptyState from '../../shared/components/EmptyState.vue';
import { get, post, put, upload } from '../../shared/api';
import { askConfirmation } from '../../shared/confirm';
import type { ApiError } from '../../shared/api';
import { formatTime } from '../../shared/format';
import { useAuthStore } from '../../shared/stores/auth';
import type {
  ImageUploadResponse,
  ConversationSummary,
  MessageItem,
  MessagePage,
  SendMessageResponse,
} from '../../shared/types';
import { useMessageSocket } from './socket';

const MAX_BODY = 2000;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES = ['image/jpeg', 'image/png'];

const route = useRoute();
const auth = useAuthStore();

const conversationId = computed(() => Number(route.params.id));
const myId = computed(() => auth.me?.id ?? 0);
const otherUser = ref<ConversationSummary | null>(null),
  blockState = ref<{
    blockedByMe: boolean;
    blockedEitherDirection: boolean;
  } | null>(null),
  blockBusy = ref(false),
  blockError = ref('');
const reportLink = computed(() => ({
  path: '/support',
  query: {
    title: `私信举报：会话 #${conversationId.value}`,
    body: `举报对象：${otherUser.value?.otherNickname ?? '会话对方'}\n会话编号：${conversationId.value}\n请补充具体问题、发生时间与证据说明。`,
  },
}));
async function loadBlockState() {
  const requestedId = conversationId.value;
  blockError.value = '';
  try {
    if (!otherUser.value) {
      let page = 0;
      while (requestedId === conversationId.value) {
        const batch = await get<ConversationSummary[]>(
          '/messages/conversations',
          { page, size: 50 },
        );
        if (requestedId !== conversationId.value) return;
        otherUser.value = batch.find((c) => c.id === requestedId) ?? null;
        if (otherUser.value || batch.length < 50) break;
        page++;
      }
    }
    if (!otherUser.value) return;
    const result = await get<{
      blockedByMe: boolean;
      blockedEitherDirection: boolean;
    }>(`/messages/users/${otherUser.value.otherUserId}/block`);
    if (requestedId === conversationId.value) blockState.value = result;
  } catch (e) {
    if (requestedId === conversationId.value)
      blockError.value = (e as ApiError).message || '屏蔽状态读取失败';
  }
}
async function toggleBlock() {
  if (!otherUser.value || !blockState.value || blockBusy.value) return;
  const blocked = !blockState.value.blockedByMe;
  if (
    !(await askConfirmation(
      blocked
        ? '确认屏蔽对方？双方将不能发送新私信或图片，历史记录和订单通知保留。'
        : '确认取消屏蔽对方？若对方仍在屏蔽你，双方仍不能发送消息。',
    ))
  )
    return;
  blockBusy.value = true;
  blockError.value = '';
  try {
    blockState.value = await put(
      `/messages/users/${otherUser.value.otherUserId}/block`,
      { blocked },
    );
  } catch (e) {
    blockError.value = (e as ApiError).message;
  } finally {
    blockBusy.value = false;
  }
}

/** 消息按时间正序展示（接口 items 为 id 倒序，加载后反转） */
const messages = ref<MessageItem[]>([]);
const hasMore = ref(false);
const nextBeforeId = ref<number | null>(null);
const loading = ref(true);
const loadingMore = ref(false);
const error = ref('');

const draft = ref('');
const sending = ref(false);
const sendError = ref('');
/** 文字发送失败重试时保留同一 clientId（服务端按 senderId+clientId 幂等去重） */
const pendingText = ref<{ clientId: string; body: string } | null>(null);

const imageInput = ref<HTMLInputElement | null>(null);
const uploadingImage = ref(false);
const imageError = ref('');
/** 图片已上传但发送失败时保留 attachmentId+clientId，点重试不再重复上传 */
const pendingImage = ref<{ clientId: string; attachmentId: string } | null>(
  null,
);

const listEl = ref<HTMLElement | null>(null);
/** 已上报的已读位置，已读不倒退 */
let lastReadThroughId = 0;

function scrollToBottom() {
  void nextTick(() => {
    const el = listEl.value;
    if (el) el.scrollTop = el.scrollHeight;
    void markRead();
  });
}

/** 仅上报本会话已实际展示的最大消息 id；已读不倒退 */
async function markRead() {
  await nextTick();
  if (document.visibilityState !== 'visible' || !listEl.value) return;
  const bounds = listEl.value.getBoundingClientRect();
  const visible = [
    ...listEl.value.querySelectorAll<HTMLElement>('[data-message-id]'),
  ].filter((el) => {
    const r = el.getBoundingClientRect();
    return r.bottom > bounds.top && r.top < bounds.bottom;
  });
  const maxId = Math.max(
    0,
    ...visible.map((el) => Number(el.dataset.messageId) || 0),
  );
  if (maxId === 0 || maxId <= lastReadThroughId) return;
  try {
    await post(`/messages/conversations/${conversationId.value}/read`, {
      throughId: maxId,
    });
    lastReadThroughId = maxId;
  } catch {
    // 已读上报失败静默，下一轮刷新再试
  }
}

async function loadInitial() {
  const requestedId = conversationId.value;
  loading.value = true;
  error.value = '';
  try {
    const pageData = await get<MessagePage>(
      `/messages/conversations/${conversationId.value}`,
      {
        size: 30,
      },
    );
    if (requestedId !== conversationId.value) return;
    messages.value = [...pageData.items].reverse();
    hasMore.value = pageData.hasMore;
    nextBeforeId.value = pageData.nextBeforeId;
    await markRead();
    scrollToBottom();
    void loadBlockState();
  } catch (e) {
    error.value = (e as ApiError).message || '消息加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadMore() {
  if (!hasMore.value || nextBeforeId.value === null || loadingMore.value)
    return;
  loadingMore.value = true;
  const requestedId = conversationId.value;
  try {
    const pageData = await get<MessagePage>(
      `/messages/conversations/${conversationId.value}`,
      {
        beforeId: nextBeforeId.value,
        size: 30,
      },
    );
    if (requestedId !== conversationId.value) return;
    messages.value = [...[...pageData.items].reverse(), ...messages.value];
    hasMore.value = pageData.hasMore;
    nextBeforeId.value = pageData.nextBeforeId;
  } catch (e) {
    error.value = (e as ApiError).message || '历史消息加载失败';
  } finally {
    loadingMore.value = false;
  }
}

/** 重拉最新一页并按 id 合并，保留已加载的更早历史 */
async function refreshLatest() {
  const requestedId = conversationId.value;
  const el = listEl.value;
  const atBottom = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 90;
  try {
    const pageData = await get<MessagePage>(
      `/messages/conversations/${conversationId.value}`,
      {
        size: 30,
      },
    );
    if (requestedId !== conversationId.value) return;
    // Reconnect may miss more than one page. Fill the gap until the last known message.
    const previousLatest = messages.value.at(-1)?.id;
    let cursor = pageData.nextBeforeId;
    let more = pageData.hasMore;
    while (previousLatest && more && cursor && cursor > previousLatest) {
      const gap = await get<MessagePage>(
        `/messages/conversations/${requestedId}`,
        { size: 30, beforeId: cursor },
      );
      if (requestedId !== conversationId.value) return;
      pageData.items.push(...gap.items);
      more = gap.hasMore;
      cursor = gap.nextBeforeId;
    }
    const known = new Map<number, MessageItem>(
      messages.value.map((m) => [m.id, m]),
    );
    for (const item of pageData.items) known.set(item.id, item);
    messages.value = [...known.values()].sort((a, b) => a.id - b.id);
    if (
      pendingText.value &&
      messages.value.some(
        (m) =>
          m.senderId === myId.value &&
          m.clientId === pendingText.value?.clientId,
      )
    ) {
      pendingText.value = null;
      draft.value = '';
      sendError.value = '';
    }
    if (!hasMore.value && pageData.hasMore) {
      hasMore.value = true;
      nextBeforeId.value = pageData.nextBeforeId;
    }
    await markRead();
    if (atBottom) scrollToBottom();
  } catch {
    // 刷新失败保留现有列表
  }
}

function appendMessage(msg: MessageItem) {
  if (
    messages.value.some((m) => m.id === msg.id || m.clientId === msg.clientId)
  )
    return;
  messages.value = [...messages.value, msg];
  scrollToBottom();
}

async function sendText() {
  const requestedId = conversationId.value;
  const body = pendingText.value?.body ?? draft.value.trim();
  if (!body || sending.value || blockState.value?.blockedEitherDirection)
    return;
  if (body.length > MAX_BODY) {
    sendError.value = `消息最多 ${MAX_BODY} 字符`;
    return;
  }
  sending.value = true;
  sendError.value = '';
  if (!pendingText.value)
    pendingText.value = { clientId: crypto.randomUUID(), body };
  try {
    const msg = await post<SendMessageResponse>(
      `/messages/conversations/${conversationId.value}`,
      {
        clientId: pendingText.value.clientId,
        body,
      },
    );
    if (requestedId !== conversationId.value) return;
    appendMessage(msg);
    draft.value = '';
    pendingText.value = null;
  } catch (e) {
    // 保留草稿与 clientId，再次点击发送即为幂等重试
    sendError.value = ((e as ApiError).message || '发送失败') + '，请重试';
    void loadBlockState();
  } finally {
    sending.value = false;
  }
}

function pickImage() {
  if (blockState.value?.blockedEitherDirection) return;
  imageInput.value?.click();
}

async function onImagePicked(ev: Event) {
  const requestedId = conversationId.value;
  const input = ev.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || blockState.value?.blockedEitherDirection) return;
  imageError.value = '';
  if (!IMAGE_TYPES.includes(file.type)) {
    imageError.value = '仅支持 JPG/PNG 图片';
    return;
  }
  if (file.size > MAX_IMAGE_BYTES) {
    imageError.value = '图片不能超过 5MB';
    return;
  }
  uploadingImage.value = true;
  try {
    const form = new FormData();
    form.append('file', file);
    const uploaded = await upload<ImageUploadResponse>(
      `/messages/conversations/${conversationId.value}/images`,
      form,
    );
    if (requestedId !== conversationId.value) return;
    pendingImage.value = {
      clientId: crypto.randomUUID(),
      attachmentId: uploaded.id,
    };
    await sendImageMessage();
  } catch (e) {
    imageError.value = (e as ApiError).message || '图片上传失败，请重试';
    pendingImage.value = null;
  } finally {
    uploadingImage.value = false;
  }
}

/** 用 pendingImage 里的 attachmentId 发消息；失败保留 pendingImage 供幂等重试 */
async function sendImageMessage() {
  const requestedId = conversationId.value;
  const pending = pendingImage.value;
  if (!pending || blockState.value?.blockedEitherDirection) return;
  try {
    const msg = await post<SendMessageResponse>(
      `/messages/conversations/${conversationId.value}`,
      {
        clientId: pending.clientId,
        attachmentId: pending.attachmentId,
      },
    );
    if (requestedId !== conversationId.value) return;
    appendMessage(msg);
    pendingImage.value = null;
    imageError.value = '';
  } catch (e) {
    imageError.value = ((e as ApiError).message || '图片发送失败') + '，';
  }
}

async function retryImageSend() {
  if (!pendingImage.value || uploadingImage.value) return;
  uploadingImage.value = true;
  try {
    await sendImageMessage();
  } finally {
    uploadingImage.value = false;
  }
}

// ready（含重连恢复）重拉最新页补漏通知；messages.changed 匹配本会话时刷新并已读
const { statusText } = useMessageSocket({
  onReady: () => {
    if (!loading.value) void refreshLatest();
  },
  onChanged: (cid) => {
    if (cid === conversationId.value) void refreshLatest();
  },
});

function resetState() {
  otherUser.value = null;
  blockState.value = null;
  blockError.value = '';
  messages.value = [];
  hasMore.value = false;
  nextBeforeId.value = null;
  error.value = '';
  sendError.value = '';
  imageError.value = '';
  draft.value = '';
  pendingText.value = null;
  pendingImage.value = null;
  lastReadThroughId = 0;
}

watch(conversationId, (id, prev) => {
  if (!Number.isFinite(id) || id === prev) return;
  resetState();
  void loadInitial();
});

function handleVisibility() {
  if (document.visibilityState === 'visible') {
    void refreshLatest();
    void loadBlockState();
  }
}
onMounted(() => {
  void loadInitial();
  document.addEventListener('visibilitychange', handleVisibility);
});
onBeforeUnmount(() =>
  document.removeEventListener('visibilitychange', handleVisibility),
);
</script>

<style scoped>
.mm-chat {
  max-width: 1040px;
  margin: 0 auto;
  padding: var(--mm-space-5) var(--mm-space-4);
}

.mm-chat__back {
  font-size: var(--mm-font-s);
  color: var(--mm-primary);
}

.mm-chat__conn {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  margin-bottom: var(--mm-space-3);
}

.mm-chat__error {
  font-size: var(--mm-font-s);
  color: var(--mm-danger);
  margin-bottom: var(--mm-space-2);
}

.mm-chat__retry {
  background: none;
  border: none;
  padding: 0;
  color: var(--mm-primary);
  font-size: var(--mm-font-s);
  text-decoration: underline;
  cursor: pointer;
}

.mm-chat__list {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
  height: min(55vh, 500px);
  min-height: 260px;
  overflow-y: auto;
  padding: 22px 18px;
  background: #f1f4ee;
  border: 1px solid #e1e6dd;
  border-radius: 6px;
}

.mm-chat__more {
  align-self: center;
  background: none;
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  padding: var(--mm-space-1) var(--mm-space-3);
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  cursor: pointer;
}

.mm-chat__more:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.mm-chat__hint {
  color: var(--mm-muted);
  font-size: var(--mm-font-s);
  text-align: center;
  padding: var(--mm-space-5) 0;
}

.mm-chat__row {
  display: flex;
  justify-content: flex-start;
}

.mm-chat__row.is-mine {
  justify-content: flex-end;
}

.mm-chat__bubble {
  max-width: 75%;
  padding: var(--mm-space-2) var(--mm-space-3);
  border-radius: var(--mm-radius-l);
  background-color: var(--mm-canvas);
  color: var(--mm-ink);
}

.mm-chat__row.is-mine .mm-chat__bubble {
  background-color: #dce8cf;
  color: #2e3b26;
}

.mm-chat__image {
  display: block;
  max-width: 220px;
  max-height: 220px;
  border-radius: var(--mm-radius-m);
}

.mm-chat__text {
  white-space: pre-wrap;
  word-break: break-word;
}

.mm-chat__time {
  display: block;
  margin-top: var(--mm-space-1);
  font-size: var(--mm-font-s);
  opacity: 0.7;
}

.mm-chat__composer {
  display: flex;
  align-items: center;
  gap: var(--mm-space-2);
  margin-top: var(--mm-space-3);
}

.mm-chat__composer input[type='text'],
.mm-chat__composer input:not([type]) {
  flex: 1;
  min-width: 0;
  min-height: 40px;
  padding: 0 var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
}

.mm-chat__count {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  white-space: nowrap;
}
</style>
