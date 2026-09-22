<script setup lang="ts">
import { ratingSourceText } from "./types";
import { ref, watch, onBeforeUnmount, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { get, post, del, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import { askConfirmation } from '../../shared/confirm';
import { formatTime } from '../../shared/format';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import ReportButton from './ReportButton.vue';
import type { CommunityPage, Rating } from './types';
interface Comment {
  id: number;
  authorId: number;
  nickname: string;
  avatarUrl: string | null;
  seller: boolean;
  content: string;
  replyToId: number | null;
  replyToNickname: string | null;
  replyPreview: string | null;
  createdAt: string;
}
const props = defineProps<{ productId: number }>(),
  auth = useAuthStore(),
  route = useRoute();
const comments = ref<Comment[]>([]),
  ratings = ref<Rating[]>([]),
  total = ref(0),
  ratingTotal = ref(0),
  page = ref(0),
  pages = ref(0),
  ratingPage = ref(0),
  ratingPages = ref(0);
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  ratingError = ref(''),
  draft = ref(''),
  reply = ref<Comment | null>(null),
  editor = ref<HTMLTextAreaElement | null>(null);
let generation = 0;
async function loadComments() {
  const run = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const r = await get<CommunityPage<Comment>>(
      `/products/${props.productId}/comments`,
      { page: page.value, size: 10 },
    );
    if (run !== generation) return;
    comments.value = r.items;
    const target = /^#discussion-(\d+)$/.exec(route.hash)?.[1];
    if (target && !r.items.some(item => item.id === Number(target))) {
      const linked = await get<Comment>(`/products/${props.productId}/comments/${target}`);
      if (run !== generation) return;
      comments.value = [linked, ...r.items];
    }
    total.value = r.total;
    pages.value = r.totalPages;
    if (target) { await nextTick(); document.getElementById(`discussion-${target}`)?.scrollIntoView({ block: 'center' }); }
  } catch (e) {
    if (run === generation) error.value = (e as ApiError).message;
  } finally {
    if (run === generation) loading.value = false;
  }
}
let ratingGeneration = 0;
async function loadRatings() {
  const run = ++ratingGeneration;
  ratingError.value = '';
  try {
    const r = await get<CommunityPage<Rating>>(
      `/products/${props.productId}/ratings`,
      { page: ratingPage.value, size: 8 },
    );
    if (run === ratingGeneration) {
      ratings.value = r.items;
      ratingTotal.value = r.total;
      ratingPages.value = r.totalPages;
    }
  } catch (e) {
    if (run === ratingGeneration) ratingError.value = (e as ApiError).message;
  }
}
async function respond(comment: Comment) {
  reply.value = comment;
  await nextTick();
  editor.value?.focus();
  editor.value?.scrollIntoView({
    block: 'center',
    behavior: matchMedia('(prefers-reduced-motion: reduce)').matches
      ? 'instant'
      : 'smooth',
  });
}
async function submit() {
  if (busy.value || !draft.value.trim()) return;
  const run = generation,
    owner = auth.me?.id,
    product = props.productId;
  busy.value = true;
  error.value = '';
  try {
    await post(`/products/${props.productId}/comments`, {
      content: draft.value.trim(),
      replyToId: reply.value?.id ?? null,
    });
    if (run !== generation) return;
    draft.value = '';
    reply.value = null;
    page.value = 0;
    await loadComments();
  } catch (e) {
    if (run === generation) error.value = (e as ApiError).message;
  } finally {
    if (owner === auth.me?.id && product === props.productId)
      busy.value = false;
  }
}
async function remove(comment: Comment) {
  const owner = auth.me?.id,
    product = props.productId;
  if (!(await askConfirmation('删除这条留言？'))) return;
  if (owner !== auth.me?.id || product !== props.productId) return;
  try {
    await del(`/products/${product}/comments/${comment.id}`);
    if (product === props.productId) await loadComments();
  } catch (e) {
    if (product === props.productId) error.value = (e as ApiError).message;
  }
}
watch(
  () => [props.productId, auth.me?.id],
  () => {
    generation++;
    ratingGeneration++;
    page.value = 0;
    ratingPage.value = 0;
    comments.value = [];
    ratings.value = [];
    draft.value = '';
    reply.value = null;
    busy.value = false;
    void loadComments();
    void loadRatings();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  generation++;
  ratingGeneration++;
});
watch(() => route.hash, hash => { if (hash.startsWith('#discussion-')) void loadComments(); });
</script>
<template>
  <section
    id="product-ratings"
    class="product-talk"
    aria-labelledby="product-ratings-title"
  >
    <header>
      <div>
        <span class="product-talk__eyebrow">来自购买者的反馈</span>
        <h2 id="product-ratings-title">
          买家评价 <span>{{ ratingTotal }}</span>
        </h2>
      </div>
      <p>完成交易后，由购买者留下评价</p>
    </header>
    <p v-if="ratingError" role="alert">
      {{ ratingError }} <button @click="loadRatings">重新加载</button>
    </p>
    <div v-else-if="!ratings.length" class="product-talk__empty">
      <strong>还没有成交评价</strong>
      <p>想了解这件物品，可以先在下方留言，或联系卖家询问细节。</p>
    </div>
    <article
      v-for="rating in ratings"
      :key="rating.id"
      class="product-talk__entry"
    >
      <UserAvatar
        :src="rating.reviewerAvatarUrl"
        :nickname="rating.reviewerNickname"
        :size="38"
      />
      <div>
        <RouterLink :to="`/sellers/${rating.reviewerId}`">{{
          rating.reviewerNickname
        }}</RouterLink
        ><span
          class="product-talk__stars"
          :aria-label="`${rating.rating}分，满分5分`"
          >{{ '★'.repeat(rating.rating)
          }}{{ '☆'.repeat(5 - rating.rating) }}</span
        >
        <small v-if="rating.paymentSource!=='LIVE'" class="product-talk__source"
          >{{ ratingSourceText[rating.paymentSource || 'UNVERIFIED'] }}</small
        >
        <p>{{ rating.comment || '买家没有填写文字评价' }}</p>
        <time>{{ formatTime(rating.createdAt) }}</time
        ><ReportButton
          v-if="auth.me"
          resource-type="ORDER_REVIEW"
          :resource-id="rating.id"
        />
      </div>
    </article>
    <MmPagination
      :page="ratingPage"
      :total-pages="ratingPages"
      @change="
        ratingPage = $event;
        loadRatings();
      "
    />
  </section>
  <section
    id="product-discussion"
    class="product-talk"
    aria-labelledby="product-discussion-title"
  >
    <header>
      <div>
        <span class="product-talk__eyebrow">问清楚，再决定</span>
        <h2 id="product-discussion-title">
          留言讨论 <span>{{ total }}</span>
        </h2>
      </div>
      <p>聊成色、配件和交付，也欢迎卖家补充说明</p>
    </header>
    <form v-if="auth.me" class="product-talk__compose" @submit.prevent="submit">
      <div v-if="reply" class="product-talk__replying">
        回复 {{ reply.nickname }}：{{ reply.content.slice(0, 65) }}
        <button type="button" @click="reply = null">取消回复</button>
      </div>
      <label class="mm-visually-hidden" for="product-comment">留言内容</label
      ><textarea
        id="product-comment"
        ref="editor"
        v-model="draft"
        maxlength="1000"
        rows="3"
        placeholder="想了解物品的哪些细节？"
        :disabled="busy"
        required
      />
      <div>
        <small>公开留言，请勿填写电话和完整地址</small
        ><button type="submit" :disabled="busy || !draft.trim()">
          {{ busy ? '正在发布…' : reply ? '发布回复' : '发布留言' }}
        </button>
      </div>
    </form>
    <p v-else class="product-talk__login">
      <RouterLink :to="{ path: '/login', query: { redirect: route.fullPath } }"
        >登录后参与讨论</RouterLink
      ><span>也可以先看看大家都在聊什么</span>
    </p>
    <p v-if="error" role="alert">
      {{ error }} <button type="button" @click="loadComments">重新加载</button>
    </p>
    <p v-if="loading" role="status">正在加载留言…</p>
    <div v-else-if="!comments.length && !error" class="product-talk__empty">
      <strong>来聊聊这件好物</strong>
      <p>问问使用情况、配件是否齐全，或与卖家商量交付方式。</p>
    </div>
    <article
      v-for="comment in comments"
      :key="comment.id"
      :id="`discussion-${comment.id}`"
      class="product-talk__entry"
    >
      <UserAvatar
        :src="comment.avatarUrl"
        :nickname="comment.nickname"
        :size="38"
      />
      <div class="product-talk__content">
        <div class="product-talk__author">
          <RouterLink :to="`/sellers/${comment.authorId}`">{{
            comment.nickname
          }}</RouterLink
          ><span v-if="comment.seller" class="product-talk__seller">卖家</span
          ><time>{{ formatTime(comment.createdAt) }}</time>
        </div>
        <blockquote v-if="comment.replyToId">
          {{
            comment.replyToNickname
              ? `回复 ${comment.replyToNickname}：${comment.replyPreview}`
              : '原留言已不可见'
          }}
        </blockquote>
        <p>{{ comment.content }}</p>
        <div v-if="auth.me" class="product-talk__actions">
          <button type="button" @click="respond(comment)">回复</button
          ><button
            v-if="auth.me.id === comment.authorId"
            type="button"
            @click="remove(comment)"
          >
            删除</button
          ><ReportButton
            v-else
            resource-type="PRODUCT_COMMENT"
            :resource-id="comment.id"
          />
        </div>
      </div>
    </article>
    <MmPagination
      :page="page"
      :total-pages="pages"
      @change="
        page = $event;
        loadComments();
      "
    />
  </section>
</template>
<style scoped>
.product-talk__source {
  display: block;
  width: fit-content;
  margin: 7px 0;
  padding: 3px 8px;
  color: var(--mm-muted);
  background: var(--mm-canvas);
  border: 1px solid var(--mm-border);
  border-radius: 4px;
  font-size: 11px;
}
.product-talk {
  scroll-margin-top: 150px;
  background: #fff;
  border: 1px solid var(--mm-border);
  border-radius: 18px;
  padding: 28px;
  margin: 24px 0;
}
.product-talk header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid var(--mm-border);
  padding-bottom: 20px;
  margin-bottom: 24px;
}
.product-talk header p {
  font-size: 12px;
  color: var(--mm-muted);
}
.product-talk h2 {
  font-size: 23px;
  margin: 7px 0 0;
}
.product-talk h2 span {
  font-size: 14px;
  color: var(--mm-muted);
  margin-left: 8px;
}
.product-talk__eyebrow {
  font-size: 11px;
  color: #8a7359;
  letter-spacing: 1px;
}
.product-talk__empty {
  text-align: center;
  padding: 28px 12px;
  color: var(--mm-muted);
  font-size: 13px;
}
.product-talk__empty strong {
  font-size: 16px;
  color: var(--mm-ink);
}
.product-talk__entry {
  display: flex;
  gap: 14px;
  padding: 23px 0;
  border-bottom: 1px solid #efede7;
  font-size: 14px;
}
.product-talk__content {
  min-width: 0;
  flex: 1;
}
.product-talk__entry p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.85;
}
.product-talk__author {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.product-talk time {
  font-size: 11px;
  color: var(--mm-muted);
}
.product-talk__author time {
  margin-left: auto;
}
.product-talk__seller {
  font-size: 10px;
  border-radius: 4px;
  padding: 3px 7px;
  background: #e5eee7;
  color: #386449;
}
.product-talk__stars {
  color: #b46b2d;
  margin-left: 15px;
  letter-spacing: 2px;
}
.product-talk blockquote {
  margin: 12px 0 0;
  padding: 9px 12px;
  background: #f6f5f1;
  border-left: 2px solid #d4c8b9;
  color: #827464;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.product-talk__actions {
  display: flex;
  align-items: center;
  gap: 18px;
}
.product-talk__actions > button {
  background: none;
  border: 0;
  padding: 4px 0;
  color: #78644c;
  cursor: pointer;
  font-size: 12px;
}
.product-talk__compose {
  background: #f7f6f2;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  padding: 15px;
}
.product-talk textarea {
  width: 100%;
  border: 0;
  resize: vertical;
  background: transparent;
  font: inherit;
  font-size: 14px;
  line-height: 1.7;
  min-height: 85px;
}
.product-talk__compose > div:last-child {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  margin-top: 12px;
}
.product-talk small {
  color: var(--mm-muted);
  font-size: 11px;
}
.product-talk__compose button[type='submit'] {
  background: var(--mm-primary);
  color: white;
  border: 0;
  padding: 9px 20px;
  border-radius: 8px;
  cursor: pointer;
  white-space: nowrap;
}
.product-talk button:disabled {
  opacity: 0.5;
  cursor: wait;
}
.product-talk__replying {
  font-size: 12px;
  padding: 0 0 10px;
  color: #7a654e;
}
.product-talk__replying button {
  border: 0;
  background: transparent;
  color: var(--mm-primary);
  cursor: pointer;
}
.product-talk__login {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 18px;
  background: #f7f6f2;
  border-radius: 10px;
}
.product-talk__login span {
  color: var(--mm-muted);
}
@media (max-width: 600px) {
  .product-talk {
    padding: 20px 16px;
  }
  .product-talk header {
    display: block;
  }
  .product-talk__author time {
    width: 100%;
    margin-left: 0;
  }
  .product-talk__login {
    display: grid;
    gap: 9px;
  }
  .product-talk__compose > div:last-child {
    align-items: end;
  }
  .product-talk h2 {
    font-size: 21px;
  }
}
</style>
