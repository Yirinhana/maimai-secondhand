<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { get } from '../../shared/api';
import type { SellerApplication } from '../../shared/types';

type PublishingApplication = SellerApplication & {
  publishingMode?: 'SIMULATED' | 'REAL_PAYMENT';
  canPublish?: boolean;
  publishingMessage?: string;
};
const props = defineProps<{
  ownerId: number;
  application?: PublishingApplication | null;
}>();
const data = ref<PublishingApplication | null>(null);
const loading = ref(false), error = ref(false);
let generation = 0, disposed = false;
const known = computed(() =>
  (data.value?.publishingMode === 'SIMULATED' || data.value?.publishingMode === 'REAL_PAYMENT') &&
  typeof data.value.canPublish === 'boolean',
);
async function load() {
  const version = ++generation;
  data.value = null;
  error.value = false;
  loading.value = false;
  if (props.application !== undefined) {
    data.value = props.application;
    loading.value = false;
    return;
  }
  if (!props.ownerId) return;
  loading.value = true;
  try {
    const result = await get<PublishingApplication | null>('/me/seller-application');
    if (!disposed && version === generation) data.value = result;
  } catch {
    if (!disposed && version === generation) error.value = true;
  } finally {
    if (!disposed && version === generation) loading.value = false;
  }
}
watch(() => [props.ownerId, props.application], load, { immediate: true });
onBeforeUnmount(() => { disposed = true; generation++; });
</script>

<template>
  <section class="seller-publishing" aria-label="发布与收款说明" role="status">
    <p v-if="loading">正在核对当前发布条件…</p>
    <p v-else-if="error">
      暂未读取到发布条件，已填写内容仍会保留。
      <button type="button" @click="load">重新检查</button>
    </p>
    <template v-else-if="known">
      <div class="seller-publishing__heading">
        <strong>{{ data?.publishingMode === 'SIMULATED' ? '模拟交易发布' : '真实交易发布' }}</strong>
        <span :class="{ 'is-ready': data?.canPublish }">{{ data?.canPublish ? '可提交商品审核' : '发布条件待满足' }}</span>
      </div>
      <p>{{ data?.publishingMessage }}</p>
      <p v-if="data?.publishingMode === 'SIMULATED'" class="seller-publishing__detail">
        商品提交后仍需平台审核；模拟订单不扣款、不实际发货，也不代表已开通真实收款。
      </p>
    </template>
    <p v-else>商品发布与收款资格分别核验。提交商品后仍需平台审核，交易方式以当前平台开放的能力为准。</p>
  </section>
</template>

<style scoped>
.seller-publishing { padding: 16px 18px; border: 1px solid var(--mm-zone-border, #d9dfce); border-radius: 10px; background: var(--mm-zone-soft, #f4f6ef); font-size: 13px; line-height: 1.8; min-width: 0; }
.seller-publishing p { margin: 0; overflow-wrap: anywhere; }
.seller-publishing__heading { display: flex; align-items: center; flex-wrap: wrap; gap: 8px 12px; margin-bottom: 6px; }
.seller-publishing__heading strong { color: var(--mm-ink); font-size: 14px; }
.seller-publishing__heading span { font-size: 12px; padding: 2px 8px; border-radius: 5px; background: #f5ebd7; color: #755323; }
.seller-publishing__heading .is-ready { background: #e4eddd; color: #3d5d32; }
.seller-publishing__detail { color: var(--mm-muted); margin-top: 5px !important; }
.seller-publishing button { min-height: 40px; color: var(--mm-primary); text-decoration: underline; }
</style>
