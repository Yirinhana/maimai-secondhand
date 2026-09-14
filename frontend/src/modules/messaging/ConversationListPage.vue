<template>
  <section class="mm-inbox">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">MESSAGES</p>
        <h1>私信</h1>
        <p>关于好物的沟通，都留在这里。</p>
      </div>
      <span v-if="statusText" class="mm-inbox__connection" role="status">{{
        statusText
      }}</span>
    </header>
    <div class="mm-inbox__layout">
      <section class="mm-inbox__mailbox">
        <div class="mm-inbox__label">
          <h2>收件箱</h2>
          <span>第 {{ page + 1 }} 页</span>
        </div>
        <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
        <p v-if="loading" class="mm-inbox__hint">加载中…</p>
        <EmptyState
          v-else-if="!conversations.length"
          title="还没有会话"
          description="发现感兴趣的商品后，点击联系卖家开始沟通"
        />
        <ul v-else class="mm-inbox__list">
          <li v-for="c in conversations" :key="c.id">
            <button type="button" class="mm-inbox__item" @click="open(c.id)">
              <UserAvatar :nickname="c.otherNickname" :size="46" /><span
                class="mm-inbox__content"
                ><span class="mm-inbox__row"
                  ><strong>{{ c.otherNickname }}</strong
                  ><time>{{ formatTime(c.updatedAt) }}</time></span
                ><span class="mm-inbox__row"
                  ><span class="mm-inbox__preview">{{ preview(c) }}</span
                  ><span v-if="c.unread > 0" class="mm-inbox__unread">{{
                    c.unread > 99 ? '99+' : c.unread
                  }}</span></span
                ></span
              >
            </button>
          </li>
        </ul>
        <div
          v-if="page > 0 || conversations.length === size"
          class="mm-inbox__pager"
        >
          <MmButton
            variant="ghost"
            :disabled="page === 0 || loading"
            @click="goPage(page - 1)"
            >上一页</MmButton
          ><MmButton
            variant="ghost"
            :disabled="conversations.length < size || loading"
            @click="goPage(page + 1)"
            >下一页</MmButton
          >
        </div>
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
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import MmButton from '../../shared/components/MmButton.vue';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import MmIcon from '../../shared/components/MmIcon.vue';
import EmptyState from '../../shared/components/EmptyState.vue';
import { get } from '../../shared/api';
import type { ApiError } from '../../shared/api';
import { formatTime } from '../../shared/format';
import type { ConversationSummary } from '../../shared/types';
import { useMessageSocket } from './socket';

const router = useRouter();

const conversations = ref<ConversationSummary[]>([]);
const page = ref(0);
const size = 20;
const loading = ref(false);
const error = ref('');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    conversations.value = await get<ConversationSummary[]>(
      '/messages/conversations',
      {
        page: page.value,
        size,
      },
    );
  } catch (e) {
    error.value = (e as ApiError).message || '会话列表加载失败';
  } finally {
    loading.value = false;
  }
}

function goPage(p: number) {
  page.value = p;
  void load();
}

function open(id: number) {
  router.push(`/messages/${id}`);
}

function preview(c: ConversationSummary): string {
  const lm: unknown = c.lastMessage;
  if (lm === null || lm === undefined || lm === '') return '暂无消息';
  if (typeof lm === 'string') return lm;
  // 防御：服务端若返回消息对象则取正文，纯图片消息显示占位
  if (typeof lm === 'object' && 'body' in lm) {
    const body = (lm as { body?: string | null }).body;
    return body ?? '[图片]';
  }
  return String(lm);
}

// ready（含重连恢复）与 messages.changed 推送都重新拉取列表，覆盖未读数
const { statusText } = useMessageSocket({
  onReady: () => void load(),
  onChanged: () => void load(),
});

onMounted(load);
</script>

<style scoped>
.mm-inbox {
  max-width: 1180px;
  margin: auto;
  padding: 32px 28px 56px;
}
.mm-inbox__connection {
  font-size: 11px;
  color: #58725b;
  border: 1px solid #d3dfd1;
  background: #f6faf4;
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
  border: 1px solid #dce2da;
  border-radius: 8px;
  min-width: 0;
  min-height: 360px;
}
.mm-inbox__label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f9faf8;
  padding: 18px 22px;
  border-bottom: 1px solid #e5e9e2;
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
  background: #f6f8f2;
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
  color: #3f4a3e;
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
  color: #737d71;
  margin: 10px 0 18px;
}
.mm-inbox__guide a {
  color: #465e43;
  font-size: 12px;
  font-weight: 650;
}
.mm-inbox__guide > div {
  margin-top: 34px;
  padding-top: 25px;
  border-top: 1px solid #d9dfd5;
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
</style>
