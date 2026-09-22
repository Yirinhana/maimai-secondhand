<script setup lang="ts">
import { ratingSourceText, ratingRefundText } from "./types";
import { computed, onMounted, ref } from 'vue';
import { get, post, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import { formatTime } from '../../shared/format';
import type { CommunityPage, Rating } from './types';
import MmButton from '../../shared/components/MmButton.vue';
import ReportButton from './ReportButton.vue';
import WorkflowAssistant from '../support/WorkflowAssistant.vue';
const props = defineProps<{ orderId: number; readOnly?: boolean }>(),
  auth = useAuthStore(),
  items = ref<Rating[]>([]),
  stars = ref(5),
  comment = ref(''),
  error = ref(''),
  busy = ref(false);
const rated = computed(() =>
  items.value.some((r) => r.reviewerId === auth.me?.id),
);
async function load() {
  try {
    items.value = (
      await get<CommunityPage<Rating>>(
        `/community/orders/${props.orderId}/ratings`,
      )
    ).items;
  } catch (e) {
    error.value = (e as ApiError).message;
  }
}
async function submit() {
  busy.value = true;
  error.value = '';
  try {
    await post(`/community/orders/${props.orderId}/ratings`, {
      rating: stars.value,
      comment: comment.value,
    });
    await load();
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="mm-panel">
    <h2>交易评价</h2>
    <p class="mm-muted">买卖双方各可评价一次，请以实际沟通和交付情况为依据。有效评价会更新对方的站内信誉，并通知双方；模拟付款与正式渠道分别统计。</p>
    <WorkflowAssistant stage="REVIEW" :resource-id="orderId" title="整理评价要点，不替你决定评分" />
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <article v-for="item in items" :key="item.id">
      <strong>{{ item.reviewerNickname }} · {{ item.rating }} / 5 分</strong
      ><small v-if="item.paymentSource!=='LIVE'" class="mm-muted"
        >{{ ratingSourceText[item.paymentSource || 'UNVERIFIED'] }}</small
      >
      <p>{{ item.comment || '未填写文字评价' }}</p>
      <p class="mm-muted">
        {{ formatTime(item.createdAt) }} · {{ ratingRefundText[item.refundStatus] || '退款状态待核查' }}
      </p>
      <ReportButton
        v-if="item.reviewerId !== auth.me?.id"
        resource-type="ORDER_REVIEW"
        :resource-id="item.id"
      />
    </article>
    <p v-if="readOnly" class="mm-muted">历史导入记录与全额退款订单保留已有评价，不开放新增评价。</p>
    <form v-if="!rated && !readOnly" class="mm-form" @submit.prevent="submit">
      <label
        >评分<select v-model.number="stars" aria-label="评分">
          <option v-for="n in 5" :key="n" :value="n">{{ n }} 分</option>
        </select></label
      ><label>评价内容<textarea v-model="comment" maxlength="500" /></label
      ><MmButton type="submit" :loading="busy">提交评价</MmButton>
    </form>
  </section>
</template>
