<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { get, post, type ApiError } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import { formatTime } from '../../shared/format';
import type { CommunityPage, Rating } from './types';
import MmButton from '../../shared/components/MmButton.vue';
import ReportButton from './ReportButton.vue';
const props = defineProps<{ orderId: number }>(),
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
    <p class="mm-muted">买卖双方各可评价一次，退款情况会随评价显示。</p>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <article v-for="item in items" :key="item.id">
      <strong>{{ item.reviewerNickname }} · {{ item.rating }} / 5 分</strong
      ><small v-if="item.simulated" class="mm-muted"
        >体验成交评价 · 未发生真实交易</small
      >
      <p>{{ item.comment || '未填写文字评价' }}</p>
      <p class="mm-muted">
        {{ formatTime(item.createdAt) }} · 退款状态 {{ item.refundStatus }}
      </p>
      <ReportButton
        v-if="item.reviewerId !== auth.me?.id"
        resource-type="ORDER_REVIEW"
        :resource-id="item.id"
      />
    </article>
    <form v-if="!rated" class="mm-form" @submit.prevent="submit">
      <label
        >评分<select v-model.number="stars">
          <option v-for="n in 5" :key="n" :value="n">{{ n }} 分</option>
        </select></label
      ><label>评价内容<textarea v-model="comment" maxlength="500" /></label
      ><MmButton type="submit" :loading="busy">提交评价</MmButton>
    </form>
  </section>
</template>
