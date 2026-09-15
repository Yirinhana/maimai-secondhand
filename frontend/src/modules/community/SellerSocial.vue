<script setup lang="ts">
import UserAvatar from '../../shared/components/UserAvatar.vue';
import { onMounted, ref } from 'vue';
import { get, post, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import { formatTime } from '../../shared/format';
import type { CommunityPage, Rating } from './types';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import ReportButton from './ReportButton.vue';
const props = defineProps<{ sellerId: number }>(),
  auth = useAuthStore(),
  ratings = ref<Rating[]>([]),
  page = ref(0),
  pages = ref(0),
  feedback = ref(''),
  busy = ref(false);
async function load() {
  try {
    const r = await get<CommunityPage<Rating>>(
      `/community/users/${props.sellerId}/ratings`,
      { page: page.value, size: 10 },
    );
    ratings.value = r.items;
    pages.value = r.totalPages;
  } catch (e) {
    feedback.value = (e as ApiError).message;
  }
}
async function follow() {
  busy.value = true;
  try {
    await post(`/community/follows/${props.sellerId}`);
    feedback.value = '已关注，可在我的社区管理';
  } catch (e) {
    feedback.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="mm-panel seller-reviews">
    <MmButton v-if="sellerId !== auth.me?.id" :disabled="busy" @click="follow"
      >关注卖家</MmButton
    >
    <p v-if="feedback" class="mm-muted" role="status">{{ feedback }}</p>
    <h2>收到的交易评价</h2>
    <p v-if="!ratings.length" class="mm-muted">暂无公开评价</p>
    <article v-for="r in ratings" :key="r.id">
      <div class="seller-reviews__author">
        <UserAvatar
          :src="r.reviewerAvatarUrl"
          :nickname="r.reviewerNickname"
          :size="36"
        /><RouterLink :to="`/sellers/${r.reviewerId}`">{{
          r.reviewerNickname
        }}</RouterLink
        ><span>{{ r.rating }} / 5 分</span>
      </div>
      <small v-if="r.simulated" class="seller-reviews__source"
        >体验成交评价 · 未发生真实交易</small
      >
      <p>{{ r.comment || '未填写文字评价' }}</p>
      <p class="mm-muted">
        {{ formatTime(r.createdAt) }} · 退款状态 {{ r.refundStatus }}
      </p>
      <ReportButton resource-type="ORDER_REVIEW" :resource-id="r.id" />
    </article>
    <MmPagination
      :page="page"
      :total-pages="pages"
      @change="
        page = $event;
        load();
      "
    />
  </section>
</template>

<style scoped>
.seller-reviews {
  display: grid;
  gap: 18px;
}
.seller-reviews h2 {
  font-size: 19px;
}
.seller-reviews article {
  padding: 18px 0;
  border-top: 1px solid var(--mm-border);
}
.seller-reviews__author {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 14px;
}
.seller-reviews__author > span {
  margin-left: auto;
  color: var(--mm-primary);
}
.seller-reviews__source {
  display: block;
  color: var(--mm-muted);
  font-size: 11px;
  margin: 6px 0;
}
.seller-reviews article > p {
  line-height: 1.8;
  margin-bottom: 8px;
}
</style>
