<template>
  <div class="mm-chat">
    <h1 class="mm-visually-hidden">私信对话</h1>
    <MmCard title="私信会话" class="mm-chat__window">
      <template #extra>
        <RouterLink
          :to="{ path: '/messages', query: route.query }"
          class="mm-chat__back"
          >← 返回会话列表</RouterLink
        >
      </template>

      <div v-if="otherUser" class="mm-chat__peer">
        <UserAvatar
          :src="otherUser.otherAvatarUrl"
          :nickname="otherUser.otherNickname"
          :size="46"
        />
        <div class="mm-chat__identity">
          <strong>{{ otherUser.otherNickname }}</strong>
          <p>与对方的交易沟通</p>
        </div>
        <details class="mm-chat__management">
          <summary><MmIcon name="shield" />会话管理</summary>
          <div class="mm-chat__management-body">
            <p>屏蔽限制双方的新消息，历史记录保留。</p>
            <MmButton
              variant="ghost"
              :disabled="blockBusy || !blockState"
              @click="toggleBlock"
              >{{
                blockState?.blockedByMe ? '取消屏蔽' : '屏蔽对方'
              }}</MmButton
            ><RouterLink :to="reportLink">举报并联系人工客服</RouterLink
            ><MmButton
              variant="ghost"
              :disabled="blockBusy"
              @click="loadBlockState()"
              >刷新屏蔽状态</MmButton
            >
          </div>
        </details>
      </div>
      <div v-if="contextProduct" class="mm-chat__context">
        <RouterLink
          :to="`/products/${contextProduct.id}`"
          class="mm-chat__product-card"
        >
          <ItemImage
            :src="contextProduct.images[0]?.path"
            :alt="contextProduct.title"
          />
          <span
            ><small>正在咨询这件商品</small
            ><strong>{{ contextProduct.title }}</strong
            ><b>{{ formatPrice(contextProduct.priceCents) }}</b></span
          >
        </RouterLink>
        <MmButton
          variant="ghost"
          :disabled="cardSending || blockState?.blockedEitherDirection"
          @click="sendProduct"
          >{{ cardSending ? '正在发送…' : '发送商品卡片' }}</MmButton
        >
      </div>
      <p v-if="contextError" class="mm-chat__error" role="status">
        {{ contextError }}
      </p>
      <RouterLink
        v-if="!route.query.productId && otherUser?.productId"
        :to="`/products/${otherUser.productId}`"
        class="mm-chat__product"
        ><MmIcon name="box" /><span
          ><small>首次咨询的商品</small
          >{{
            otherUser.productTitle || `商品 #${otherUser.productId}`
          }}</span
        ><MmIcon name="arrow"
      /></RouterLink>
      <p v-if="blockState?.blockedEitherDirection" class="mm-notice">
        当前双方不能发送新消息或图片。历史记录仍可查看，订单通知不受影响。
      </p>
      <p v-if="blockError" class="mm-chat__error" role="alert">
        {{ blockError }}
      </p>

      <p
        class="mm-chat__conn"
        :class="{ 'is-ready': status === 'ready' }"
        role="status"
      >
        {{ status === 'ready' ? '实时消息已连接' : statusText }}
      </p>
      <div v-if="error" class="mm-recovery" role="alert">
        <p>{{ error }}</p>
        <MmButton variant="ghost" @click="loadInitial"
          >重新加载消息</MmButton
        >
      </div>

      <div
        ref="listEl"
        class="mm-chat__list"
        role="region"
        aria-label="消息记录"
        tabindex="0"
        @scroll.passive="onListScroll"
      >
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
          v-else-if="!messages.length && !error"
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
            <span class="mm-chat__sender">{{
              m.senderId === myId
                ? '我'
                : otherUser?.otherNickname || '对方'
            }}</span>
            <img
              v-if="m.attachmentUrl"
              :src="m.attachmentUrl"
              class="mm-chat__image"
              alt="图片消息"
              loading="lazy"
            />
            <p v-if="m.body" class="mm-chat__text">{{ m.body }}</p>
            <RouterLink
              v-if="m.product"
              :to="`/products/${m.product.id}`"
              class="mm-chat__product-card"
            >
              <ItemImage
                :src="m.product.coverImage"
                :alt="m.product.title"
              />
              <span
                ><small>商品详情 · 点击查看</small
                ><strong>{{ m.product.title }}</strong
                ><b>{{ formatPrice(m.product.priceCents) }}</b></span
              >
            </RouterLink>
            <time class="mm-chat__time">{{
              formatTime(m.createdAt)
            }}</time>
          </div>
        </div>
        <div
          v-if="pendingText"
          class="mm-chat__row is-mine is-pending"
          aria-live="polite"
        >
          <div class="mm-chat__bubble">
            <span class="mm-chat__sender">我</span>
            <p class="mm-chat__text">{{ pendingText.body }}</p>
            <span class="mm-chat__time">{{
              sending ? '正在发送…' : '尚未确认送达'
            }}</span>
            <button
              v-if="!sending"
              type="button"
              class="mm-chat__retry"
              @click="sendText"
            >
              重试发送
            </button>
          </div>
        </div>
      </div>
      <button
        v-if="unseenCount"
        type="button"
        class="mm-chat__new"
        @click="scrollToBottom"
      >
        {{ unseenCount > 99 ? '99+' : unseenCount }} 条新消息 · 回到最新
      </button>

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
        <textarea
          v-model="draft"
          :readonly="
            sending ||
            pendingText !== null ||
            blockState?.blockedEitherDirection
          "
          maxlength="2000"
          rows="2"
          placeholder="聊聊成色、价格或交付方式…"
          aria-label="消息内容"
          @keydown="composerKeydown"
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
          :disabled="
            loading || !draft.trim() || blockState?.blockedEitherDirection
          "
          >{{ pendingText && !sending ? '重试发送' : '发送' }}</MmButton
        >
      </form>
      <p class="mm-chat__composer-hint">
        电脑 Enter 发送，Shift + Enter
        换行；手机点击发送。草稿仅保留在当前标签页。图片最大 5MB。
      </p>
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
import MmIcon from '../../shared/components/MmIcon.vue';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import ItemImage from '../../shared/components/ItemImage.vue';
import EmptyState from '../../shared/components/EmptyState.vue';
import { get, post, put, upload } from '../../shared/api';
import { askConfirmation } from '../../shared/confirm';
import type { ApiError } from '../../shared/api';
import { formatTime, formatPrice } from '../../shared/format';
import { useAuthStore } from '../../shared/stores/auth';
import type {
  ImageUploadResponse,
  ConversationSummary,
  MessageItem,
  MessagePage,
  SendMessageResponse,
  ProductDetail,
} from '../../shared/types';
import { useMessageSocket } from './socket';
import {
  readConversationDraft,
  saveConversationDraft,
  type PendingText,
} from './conversationDraft';

const MAX_BODY = 2000;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES = ['image/jpeg', 'image/png'];

const route = useRoute();
const auth = useAuthStore();

const conversationId = computed(() => Number(route.params.id));
const myId = computed(() => auth.me?.id ?? 0);
const contextProduct = ref<ProductDetail | null>(null),
  contextError = ref(''),
  cardSending = ref(false);
let contextGeneration = 0;
let pendingCard: { clientId: string; productId: number } | null = null;
async function sendProduct() {
  if (
    !contextProduct.value ||
    cardSending.value ||
    blockState.value?.blockedEitherDirection
  )
    return;
  const run = contextGeneration,
    cid = conversationId.value;
  cardSending.value = true;
  contextError.value = '';
  pendingCard ??= {
    clientId: crypto.randomUUID(),
    productId: contextProduct.value.id,
  };
  try {
    const result = await post<MessageItem>(
      `/messages/conversations/${cid}`,
      pendingCard,
    );
    if (run !== contextGeneration) return;
    appendMessage(result);
    pendingCard = null;
  } catch (e) {
    if (run === contextGeneration)
      contextError.value =
        ((e as ApiError).message || '发送失败') + '，可再次点击重试';
  } finally {
    if (run === contextGeneration) cardSending.value = false;
  }
}
const otherUser = ref<ConversationSummary | null>(null),
  blockState = ref<{
    blockedByMe: boolean;
    blockedEitherDirection: boolean;
  } | null>(null),
  blockBusy = ref(false),
  blockError = ref('');
watch(
  () => [
    route.query.productId,
    conversationId.value,
    otherUser.value?.otherUserId,
    myId.value,
  ],
  async () => {
    const run = ++contextGeneration;
    contextProduct.value = null;
    contextError.value = '';
    pendingCard = null;
    cardSending.value = false;
    const id = Number(route.query.productId);
    if (!Number.isSafeInteger(id) || id <= 0 || !otherUser.value) return;
    try {
      const product = await get<ProductDetail>(`/products/${id}`);
      if (run !== contextGeneration) return;
      if (
        product.seller.id !== otherUser.value?.otherUserId &&
        product.seller.id !== myId.value
      ) {
        contextError.value = '这件商品不属于当前会话的卖家';
        return;
      }
      contextProduct.value = product;
    } catch (e) {
      if (run === contextGeneration)
        contextError.value = (e as ApiError).message || '商品暂不可见';
    }
  },
);
const reportLink = computed(() => ({
  path: '/support',
  query: {
    title: `私信举报：会话 #${conversationId.value}`,
    body: `举报对象：${otherUser.value?.otherNickname ?? '会话对方'}\n会话编号：${conversationId.value}\n请补充具体问题、发生时间与证据说明。`,
  },
}));
async function loadBlockState() {
  const run = epoch;
  const requestedId = conversationId.value;
  blockError.value = '';
  try {
    if (!otherUser.value) {
      const summary = await get<ConversationSummary>(
        `/messages/conversations/${requestedId}/summary`,
      );
      if (run !== epoch || requestedId !== conversationId.value) return;
      otherUser.value = summary;
    }
    if (!otherUser.value) return;
    const result = await get<{
      blockedByMe: boolean;
      blockedEitherDirection: boolean;
    }>(`/messages/users/${otherUser.value.otherUserId}/block`);
    if (run === epoch && requestedId === conversationId.value)
      blockState.value = result;
  } catch (e) {
    if (run === epoch && requestedId === conversationId.value)
      blockError.value = (e as ApiError).message || '屏蔽状态读取失败';
  }
}
async function toggleBlock() {
  const run = epoch;
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
  if (run !== epoch || !otherUser.value) return;
  blockBusy.value = true;
  blockError.value = '';
  try {
    const result = await put<NonNullable<typeof blockState.value>>(
      `/messages/users/${otherUser.value.otherUserId}/block`,
      { blocked },
    );
    if (run === epoch) blockState.value = result;
  } catch (e) {
    if (run === epoch) blockError.value = (e as ApiError).message;
  } finally {
    if (run === epoch) blockBusy.value = false;
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
const pendingText = ref<PendingText | null>(null);
const unseenCount = ref(0);
let epoch = 0;
let draftOwner = '';
let restoringDraft = false;
function rememberDraft() {
  if (!restoringDraft)
    saveConversationDraft(draftOwner, {
      text: draft.value,
      pending: pendingText.value,
    });
}
function restoreDraft() {
  restoringDraft = true;
  draftOwner =
    myId.value > 0 && conversationId.value > 0
      ? `${myId.value}:${conversationId.value}`
      : '';
  const saved = readConversationDraft(draftOwner);
  draft.value = saved.text;
  pendingText.value = saved.pending;
  restoringDraft = false;
}
watch([draft, pendingText], rememberDraft, { flush: 'sync', deep: true });
function composerKeydown(event: KeyboardEvent) {
  if (
    event.key !== 'Enter' ||
    event.shiftKey ||
    event.isComposing ||
    event.keyCode === 229 ||
    window.matchMedia('(pointer: coarse)').matches
  )
    return;
  event.preventDefault();
  void sendText();
}
function onListScroll() {
  const el = listEl.value;
  if (el && el.scrollHeight - el.scrollTop - el.clientHeight < 60)
    unseenCount.value = 0;
  void markRead();
}

const imageInput = ref<HTMLInputElement | null>(null);
const uploadingImage = ref(false);
const imageError = ref('');
/** 图片已上传但发送失败时保留 attachmentId+clientId，点重试不再重复上传 */
const pendingImage = ref<{
  clientId: string;
  attachmentId: string;
} | null>(null);

const listEl = ref<HTMLElement | null>(null);
/** 已上报的已读位置，已读不倒退 */
let lastReadThroughId = 0;

function scrollToBottom() {
  unseenCount.value = 0;
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
  const requestedId = conversationId.value,
    requestedUser = myId.value;
  const bounds = listEl.value.getBoundingClientRect();
  const visibleTop = Math.max(0, bounds.top),
    visibleBottom = Math.min(window.innerHeight, bounds.bottom);
  if (visibleBottom <= visibleTop) return;
  const visible = [
    ...listEl.value.querySelectorAll<HTMLElement>('[data-message-id]'),
  ].filter((el) => {
    const r = el.getBoundingClientRect();
    return r.bottom > visibleTop && r.top < visibleBottom;
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
    if (
      requestedId === conversationId.value &&
      requestedUser === myId.value
    )
      lastReadThroughId = Math.max(lastReadThroughId, maxId);
  } catch {
    // 已读上报失败静默，下一轮刷新再试
  }
}

async function loadInitial() {
  if (!myId.value || !conversationId.value) return;
  const run = epoch;
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
    if (run !== epoch || requestedId !== conversationId.value) return;
    messages.value = [...pageData.items].reverse();
    reconcilePending();
    hasMore.value = pageData.hasMore;
    nextBeforeId.value = pageData.nextBeforeId;
    scrollToBottom();
    void loadBlockState();
  } catch (e) {
    if (run === epoch)
      error.value = (e as ApiError).message || '消息加载失败';
  } finally {
    if (run === epoch) loading.value = false;
  }
}

async function loadMore() {
  if (!hasMore.value || nextBeforeId.value === null || loadingMore.value)
    return;
  loadingMore.value = true;
  error.value = '';
  const run = epoch;
  const requestedId = conversationId.value;
  try {
    const pageData = await get<MessagePage>(
      `/messages/conversations/${conversationId.value}`,
      {
        beforeId: nextBeforeId.value,
        size: 30,
      },
    );
    if (run !== epoch || requestedId !== conversationId.value) return;
    const previousHeight = listEl.value?.scrollHeight ?? 0;
    const previousTop = listEl.value?.scrollTop ?? 0;
    messages.value = [...[...pageData.items].reverse(), ...messages.value];
    hasMore.value = pageData.hasMore;
    nextBeforeId.value = pageData.nextBeforeId;
    await nextTick();
    if (run === epoch && listEl.value)
      listEl.value.scrollTop =
        previousTop + listEl.value.scrollHeight - previousHeight;
  } catch (e) {
    if (run === epoch)
      error.value = (e as ApiError).message || '历史消息加载失败';
  } finally {
    if (run === epoch) loadingMore.value = false;
  }
}

/** 重拉最新一页并按 id 合并，保留已加载的更早历史 */
async function refreshLatest() {
  if (loading.value || !myId.value || !conversationId.value) return;
  const run = epoch;
  const requestedId = conversationId.value;
  try {
    const pageData = await get<MessagePage>(
      `/messages/conversations/${conversationId.value}`,
      {
        size: 30,
      },
    );
    if (run !== epoch || requestedId !== conversationId.value) return;
    // Reconnect may miss more than one page. Fill the gap until the last known message.
    const previousLatest = messages.value.at(-1)?.id;
    let cursor = pageData.nextBeforeId;
    let more = pageData.hasMore;
    while (previousLatest && more && cursor && cursor > previousLatest) {
      const gap = await get<MessagePage>(
        `/messages/conversations/${requestedId}`,
        { size: 30, beforeId: cursor },
      );
      if (run !== epoch || requestedId !== conversationId.value) return;
      pageData.items.push(...gap.items);
      more = gap.hasMore;
      cursor = gap.nextBeforeId;
    }
    const known = new Map<number, MessageItem>(
      messages.value.map((m) => [m.id, m]),
    );
    const el = listEl.value;
    const previousScrollTop = el?.scrollTop ?? 0;
    const atBottom =
      !el || el.scrollHeight - el.scrollTop - el.clientHeight < 90;
    const newlyReceived = pageData.items.filter(
      (item) => !known.has(item.id) && item.senderId !== myId.value,
    ).length;
    for (const item of pageData.items) known.set(item.id, item);
    messages.value = [...known.values()].sort((a, b) => a.id - b.id);
    reconcilePending();
    if (!atBottom) unseenCount.value += newlyReceived;
    // Refresh only fills the recent gap; it must not reopen already exhausted history.
    await markRead();
    // A delayed read acknowledgement must not pull the reader back down after they scroll up.
    if (run === epoch && atBottom && listEl.value
      && Math.abs(listEl.value.scrollTop - previousScrollTop) < 1)
      scrollToBottom();
  } catch {
    // 刷新失败保留现有列表
  }
}

function reconcilePending() {
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
}

function appendMessage(msg: MessageItem) {
  if (
    messages.value.some(
      (m) =>
        m.id === msg.id ||
        (m.senderId === msg.senderId && m.clientId === msg.clientId),
    )
  )
    return;
  messages.value = [...messages.value, msg].sort((a, b) => a.id - b.id);
  scrollToBottom();
}

async function sendText() {
  const run = epoch;
  const requestedId = conversationId.value;
  const body = pendingText.value?.body ?? draft.value.trim();
  if (
    !body ||
    sending.value ||
    loading.value ||
    !myId.value ||
    blockState.value?.blockedEitherDirection
  )
    return;
  if (body.length > MAX_BODY) {
    sendError.value = `消息最多 ${MAX_BODY} 字符`;
    return;
  }
  sending.value = true;
  sendError.value = '';
  if (!pendingText.value)
    pendingText.value = {
      clientId: crypto.randomUUID(),
      body,
      productId: contextProduct.value?.id,
    };
  scrollToBottom();
  const pending = pendingText.value;
  try {
    const msg = await post<SendMessageResponse>(
      `/messages/conversations/${conversationId.value}`,
      {
        clientId: pending.clientId,
        body,
        productId: pending.productId,
      },
    );
    if (run !== epoch || requestedId !== conversationId.value) return;
    appendMessage(msg);
    draft.value = '';
    pendingText.value = null;
  } catch (e) {
    if (run !== epoch) return;
    if (
      messages.value.some(
        (m) =>
          m.senderId === myId.value && m.clientId === pending.clientId,
      )
    )
      return;
    // 保留草稿与 clientId，再次点击发送即为幂等重试
    sendError.value = ((e as ApiError).message || '发送失败') + '，请重试';
    void loadBlockState();
  } finally {
    if (run === epoch) sending.value = false;
  }
}

function pickImage() {
  if (blockState.value?.blockedEitherDirection) return;
  imageInput.value?.click();
}

async function onImagePicked(ev: Event) {
  const run = epoch;
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
    if (run !== epoch || requestedId !== conversationId.value) return;
    pendingImage.value = {
      clientId: crypto.randomUUID(),
      attachmentId: uploaded.id,
    };
    await sendImageMessage();
  } catch (e) {
    if (run !== epoch) return;
    imageError.value = (e as ApiError).message || '图片上传失败，请重试';
    pendingImage.value = null;
  } finally {
    if (run === epoch) uploadingImage.value = false;
  }
}

/** 用 pendingImage 里的 attachmentId 发消息；失败保留 pendingImage 供幂等重试 */
async function sendImageMessage() {
  const run = epoch;
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
    if (run !== epoch || requestedId !== conversationId.value) return;
    appendMessage(msg);
    pendingImage.value = null;
    imageError.value = '';
  } catch (e) {
    if (run !== epoch) return;
    imageError.value = ((e as ApiError).message || '图片发送失败') + '，';
  }
}

async function retryImageSend() {
  const run = epoch;
  if (!pendingImage.value || uploadingImage.value) return;
  uploadingImage.value = true;
  try {
    await sendImageMessage();
  } finally {
    if (run === epoch) uploadingImage.value = false;
  }
}

// ready（含重连恢复）重拉最新页补漏通知；messages.changed 匹配本会话时刷新并已读
const { status, statusText } = useMessageSocket({
  onReady: () => {
    if (!loading.value) void refreshLatest();
  },
  onChanged: (cid) => {
    if (cid === conversationId.value) void refreshLatest();
  },
});

function resetState() {
  epoch++;
  loading.value = true;
  restoringDraft = true;
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
  unseenCount.value = 0;
  sending.value = false;
  uploadingImage.value = false;
  loadingMore.value = false;
  blockBusy.value = false;
  restoreDraft();
}

watch(
  () => [conversationId.value, myId.value],
  () => {
    rememberDraft();
    resetState();
    void loadInitial();
  },
);

function handleVisibility() {
  if (document.visibilityState === 'visible') {
    void refreshLatest();
    void loadBlockState();
  }
}
onMounted(() => {
  restoreDraft();
  void loadInitial();
  document.addEventListener('visibilitychange', handleVisibility);
  window.addEventListener('scroll', markRead, { passive: true });
  window.addEventListener('resize', markRead);
});
onBeforeUnmount(() => {
  rememberDraft();
  epoch++;
  contextGeneration++;
  document.removeEventListener('visibilitychange', handleVisibility);
  window.removeEventListener('scroll', markRead);
  window.removeEventListener('resize', markRead);
});
</script>

<style scoped>
.mm-chat__context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  background: #faf8f2;
  border-bottom: 1px solid var(--mm-border);
}
.mm-chat__product-card {
  display: flex;
  gap: 12px;
  align-items: center;
  color: var(--mm-ink);
  min-width: 0;
  text-decoration: none;
}
.mm-chat__product-card > img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 8px;
  flex-shrink: 0;
}
.mm-chat__product-card > span {
  display: grid;
  gap: 5px;
  min-width: 0;
}
.mm-chat__product-card small {
  font-size: 10px;
  color: var(--mm-muted);
}
.mm-chat__product-card strong {
  font-size: 13px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.mm-chat__product-card b {
  font-size: 14px;
  color: var(--mm-primary);
}
.mm-chat__bubble .mm-chat__product-card {
  margin: 8px 0;
  padding: 10px;
  border: 1px solid #e7e0d5;
  border-radius: 10px;
  background: #fff;
  max-width: 320px;
}
@media (max-width: 600px) {
  .mm-chat__context {
    flex-wrap: wrap;
    padding: 12px;
  }
  .mm-chat__context > :last-child {
    margin-left: auto;
  }
}
.mm-chat {
  max-width: 1040px;
  margin: 0 auto;
  padding: var(--mm-space-5) var(--mm-space-4);
}
.mm-chat__peer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 18px;
}
.mm-chat__identity {
  min-width: 0;
  flex: 1;
}
.mm-chat__identity strong {
  display: block;
  overflow-wrap: anywhere;
  font-size: 17px;
}
.mm-chat__identity p {
  font-size: 12px;
  color: var(--mm-muted);
  margin-top: 2px;
}
.mm-chat__management {
  position: relative;
  flex: none;
}
.mm-chat__management summary {
  display: flex;
  gap: 7px;
  align-items: center;
  cursor: pointer;
  font-size: 12px;
  list-style: none;
  min-height: 42px;
  padding: 9px 12px;
  border: 1px solid var(--mm-zone-border);
  border-radius: 8px;
  color: var(--mm-primary);
}
.mm-chat__management summary::-webkit-details-marker {
  display: none;
}
.mm-chat__management .mm-icon {
  width: 16px;
  height: 16px;
}
.mm-chat__management-body {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 4;
  width: 255px;
  max-width: calc(100vw - 64px);
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  background: white;
  border: 1px solid var(--mm-zone-border);
  border-radius: 10px;
  box-shadow: 0 9px 28px #28423b1c;
  font-size: 13px;
}
.mm-chat__management-body p {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-chat__product {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: 14px;
  border: 1px solid var(--mm-zone-border);
  background: var(--mm-zone-soft);
  border-radius: 9px;
  color: #315c58;
  font-size: 13px;
}
.mm-chat__product .mm-icon {
  width: 19px;
  height: 19px;
  flex: none;
}
.mm-chat__product span {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.mm-chat__product small {
  display: block;
  font-size: 10px;
  color: #657f78;
  margin-bottom: 2px;
}

.mm-chat__back {
  font-size: var(--mm-font-s);
  color: var(--mm-primary);
}

.mm-chat__conn {
  font-size: 11px;
  color: var(--mm-warning);
  margin-bottom: var(--mm-space-3);
}
.mm-chat__conn.is-ready {
  color: var(--mm-primary);
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
  height: clamp(300px, 48dvh, 560px);
  overflow-y: auto;
  padding: 22px 18px;
  background: #f6f4ef;
  overscroll-behavior: contain;
  overflow-anchor: none;
  border: 1px solid var(--mm-zone-border);
  border-radius: 10px;
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
  background-color: white;
  border: 1px solid #e4e0d8;
  border-top-left-radius: 3px;
  color: var(--mm-ink);
}

.mm-chat__row.is-mine .mm-chat__bubble {
  background-color: #fae8d8;
  color: #493527;
  border-color: #e9ceba;
  border-top-left-radius: var(--mm-radius-l);
  border-top-right-radius: 3px;
}
.mm-chat__sender {
  display: block;
  font-size: 10px;
  color: #796754;
  margin-bottom: 5px;
}

.mm-chat__image {
  display: block;
  max-width: min(220px, 100%);
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
  padding-top: 16px;
  border-top: 1px solid var(--mm-zone-border);
}

.mm-chat__composer textarea {
  flex: 1;
  min-width: 0;
  min-height: 76px;
  max-height: 180px;
  resize: vertical;
  padding: 12px var(--mm-space-3);
  border: 1px solid #cfc8bd;
  background: white;
  line-height: 1.6;
  border-radius: var(--mm-radius-m);
}
.mm-chat__composer-hint {
  margin-top: 9px;
  font-size: 12px;
  color: var(--mm-muted);
}
@media (max-width: 600px) {
  .mm-chat {
    padding: 20px 12px 88px;
  }
  .mm-chat :deep(.mm-card__body) {
    padding: 16px 12px;
  }
  .mm-chat :deep(.mm-card__header) {
    padding: 15px;
  }
  .mm-chat__peer {
    flex-wrap: wrap;
    gap: 10px;
  }
  .mm-chat__management {
    margin-left: auto;
  }
  .mm-chat__management summary {
    padding: 8px;
  }
  .mm-chat__identity strong {
    font-size: 15px;
  }
  .mm-chat__list {
    height: clamp(220px, 38dvh, 340px);
    padding: 16px 10px;
  }
  .mm-chat__bubble {
    max-width: 88%;
  }
  .mm-chat__time {
    font-size: 10px;
  }
  .mm-chat__composer {
    flex-wrap: wrap;
  }
  .mm-chat__composer textarea {
    flex-basis: 100%;
    font-size: 16px;
  }
  .mm-chat__composer .mm-button {
    min-height: 44px;
    font-size: 13px;
  }
}

.mm-chat__count {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  white-space: nowrap;
}
.mm-chat__row.is-pending .mm-chat__bubble {
  border-style: dashed;
}
.mm-chat__new {
  display: block;
  margin: 10px auto 0;
  padding: 10px 18px;
  min-height: 44px;
  background: var(--mm-white);
  border: 1px solid var(--mm-primary);
  color: var(--mm-primary);
  border-radius: 22px;
  box-shadow: var(--mm-shadow);
}
.mm-chat__retry {
  min-height: 36px;
}
</style>
