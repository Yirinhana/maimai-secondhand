<template>
  <div class="mm-conversations">
    <MmCard title="私信">
      <p v-if="statusText" class="mm-conversations__conn" role="status">{{ statusText }}</p>

      <p class="mm-muted">在商品详情点击「联系卖家」，或从订单和求购联系对方。</p>
      <p v-if="error" class="mm-conversations__error" role="alert">{{ error }}</p>

      <p v-if="loading" class="mm-conversations__hint">加载中…</p>
      <EmptyState
        v-else-if="!conversations.length"
        title="还没有会话"
        description="发现感兴趣的商品后，点击联系卖家开始沟通"
      />

      <ul v-else class="mm-conversations__list">
        <li v-for="c in conversations" :key="c.id">
          <button type="button" class="mm-conversations__item" @click="open(c.id)">
            <span class="mm-conversations__row">
              <span class="mm-conversations__name">{{ c.otherNickname }}</span>
              <time class="mm-conversations__time">{{ formatTime(c.updatedAt) }}</time>
            </span>
            <span class="mm-conversations__row">
              <span class="mm-conversations__preview">{{ preview(c) }}</span>
              <span v-if="c.unread > 0" class="mm-conversations__unread">
                {{ c.unread > 99 ? '99+' : c.unread }}
              </span>
            </span>
          </button>
        </li>
      </ul>

      <div v-if="page > 0 || conversations.length === size" class="mm-conversations__pager">
        <MmButton variant="ghost" :disabled="page === 0 || loading" @click="goPage(page - 1)">
          上一页
        </MmButton>
        <span class="mm-conversations__page">第 {{ page + 1 }} 页</span>
        <MmButton
          variant="ghost"
          :disabled="conversations.length < size || loading"
          @click="goPage(page + 1)"
        >
          下一页
        </MmButton>
      </div>
    </MmCard>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import MmButton from '../../shared/components/MmButton.vue'
import MmCard from '../../shared/components/MmCard.vue'
import EmptyState from '../../shared/components/EmptyState.vue'
import { get } from '../../shared/api'
import type { ApiError } from '../../shared/api'
import { formatTime } from '../../shared/format'
import type {
  ConversationSummary,
} from '../../shared/types'
import { useMessageSocket } from './socket'

const router = useRouter()

const conversations = ref<ConversationSummary[]>([])
const page = ref(0)
const size = 20
const loading = ref(false)
const error = ref('')


async function load() {
  loading.value = true
  error.value = ''
  try {
    conversations.value = await get<ConversationSummary[]>('/messages/conversations', {
      page: page.value,
      size,
    })
  } catch (e) {
    error.value = (e as ApiError).message || '会话列表加载失败'
  } finally {
    loading.value = false
  }
}

function goPage(p: number) {
  page.value = p
  void load()
}

function open(id: number) {
  router.push(`/messages/${id}`)
}

function preview(c: ConversationSummary): string {
  const lm: unknown = c.lastMessage
  if (lm === null || lm === undefined || lm === '') return '暂无消息'
  if (typeof lm === 'string') return lm
  // 防御：服务端若返回消息对象则取正文，纯图片消息显示占位
  if (typeof lm === 'object' && 'body' in lm) {
    const body = (lm as { body?: string | null }).body
    return body ?? '[图片]'
  }
  return String(lm)
}


// ready（含重连恢复）与 messages.changed 推送都重新拉取列表，覆盖未读数
const { statusText } = useMessageSocket({
  onReady: () => void load(),
  onChanged: () => void load(),
})

onMounted(load)
</script>

<style scoped>
.mm-conversations {
  max-width: 720px;
  margin: 0 auto;
  padding: var(--mm-space-5) var(--mm-space-4);
}

.mm-conversations__conn {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  margin-bottom: var(--mm-space-3);
}

.mm-conversations__new {
  display: flex;
  gap: var(--mm-space-2);
  margin-bottom: var(--mm-space-4);
}

.mm-conversations__new input {
  flex: 1;
  min-width: 0;
  min-height: 40px;
  padding: 0 var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
}

.mm-conversations__error {
  font-size: var(--mm-font-s);
  color: var(--mm-danger);
  margin-bottom: var(--mm-space-3);
}

.mm-conversations__hint {
  color: var(--mm-muted);
  font-size: var(--mm-font-s);
  text-align: center;
  padding: var(--mm-space-5) 0;
}

.mm-conversations__list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.mm-conversations__item {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  border-top: 1px solid var(--mm-border);
  padding: var(--mm-space-3) var(--mm-space-2);
  cursor: pointer;
}

.mm-conversations__item:hover {
  background-color: var(--mm-canvas);
}

.mm-conversations__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mm-space-3);
}

.mm-conversations__name {
  font-weight: 600;
  color: var(--mm-ink);
}

.mm-conversations__time {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  white-space: nowrap;
}

.mm-conversations__preview {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mm-conversations__unread {
  flex-shrink: 0;
  min-width: 20px;
  height: 20px;
  padding: 0 var(--mm-space-1);
  border-radius: 10px;
  background-color: var(--mm-danger);
  color: var(--mm-white);
  font-size: var(--mm-font-s);
  line-height: 20px;
  text-align: center;
}

.mm-conversations__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--mm-space-3);
  margin-top: var(--mm-space-4);
}

.mm-conversations__page {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}
</style>
