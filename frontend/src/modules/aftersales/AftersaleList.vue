<template>
  <div class="mm-aftersale-list">
    <div v-if="error" class="mm-recovery" role="alert">
      <p>{{ error }}</p>
      <MmButton variant="ghost" @click="load">重新加载售后单</MmButton>
    </div>
    <p v-else-if="loading" class="mm-aftersale-list__hint">加载中…</p>
    <EmptyState
      v-else-if="items.length === 0"
      title="暂无售后单"
      :description="emptyDescription"
    />
    <template v-else>
      <MmCard
        v-for="item in items"
        :key="item.id"
        class="mm-aftersale-list__item"
      >
        <div class="mm-aftersale-list__row">
          <div class="mm-aftersale-list__main">
            <div class="mm-aftersale-list__title-line">
              <MmTag
                :text="AFTERSALE_STATUS_TEXT[item.status]"
                :tone="statusTone(item.status)"
              />
              <span class="mm-aftersale-list__type">{{
                AFTERSALE_TYPE_TEXT[item.type]
              }}</span>
            </div>
            <p class="mm-aftersale-list__meta">
              售后单号：{{ item.aftersaleNo }}
            </p>
            <p class="mm-aftersale-list__meta">
              关联订单：{{ item.orderNo }}
            </p>
            <p class="mm-aftersale-list__meta">
              申请时间：{{ formatTime(item.createdAt) }}
              <template
                v-if="
                  item.status === 'PENDING_SELLER' && item.sellerDeadline
                "
              >
                · 卖家答复截止：{{ formatTime(item.sellerDeadline) }}
              </template>
              <template
                v-else-if="
                  item.status === 'PENDING_RETURN' && item.returnDeadline
                "
              >
                · 寄回截止：{{ formatTime(item.returnDeadline) }}
              </template>
            </p>
          </div>
          <div class="mm-aftersale-list__amounts">
            <p>商品款 <PriceText :cents="item.goodsAmountCents" /></p>
            <p>运费 <PriceText :cents="item.freightAmountCents" /></p>
            <MmButton variant="ghost" @click="goDetail(item.id)"
              >查看详情</MmButton
            >
          </div>
        </div>
      </MmCard>
      <MmPagination
        :page="page"
        :total-pages="totalPages"
        @change="onPageChange"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onScopeDispose, ref, watch } from 'vue';
import { useListQuery } from '../../shared/useListQuery';
import { useRouter } from 'vue-router';
import { get } from '../../shared/api';
import type { ApiError } from '../../shared/api';
import { formatTime } from '../../shared/format';
import type {
  AftersaleStatus,
  AftersaleSummary,
  TotalPage,
} from '../../shared/types';
import {
  AFTERSALE_STATUS_TEXT,
  AFTERSALE_TYPE_TEXT,
} from '../../shared/types';
import EmptyState from '../../shared/components/EmptyState.vue';
import MmButton from '../../shared/components/MmButton.vue';
import MmCard from '../../shared/components/MmCard.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import MmTag from '../../shared/components/MmTag.vue';
import PriceText from '../../shared/components/PriceText.vue';

const props = defineProps<{
  /** 列表接口路径：/me/aftersales 或 /seller/aftersales */
  apiPath: string;
  emptyDescription?: string;
}>();

const router = useRouter();
const PAGE_SIZE = 10;

const items = ref<AftersaleSummary[]>([]);
const total = ref(0);
const { page, setPage } = useListQuery();
let sequence = 0;
const loading = ref(false);
const error = ref('');

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / PAGE_SIZE)),
);

type TagTone =
  'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral';

function statusTone(status: AftersaleStatus): TagTone {
  switch (status) {
    case 'PENDING_SELLER':
    case 'PENDING_RETURN':
    case 'RETURN_SHIPPED':
      return 'warning';
    case 'PENDING_MANUAL':
      return 'danger';
    case 'RESOLVED':
      return 'success';
    case 'SELLER_REJECTED':
      return 'info';
    default:
      return 'neutral';
  }
}

async function load() {
  const run = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    const data = await get<TotalPage<AftersaleSummary>>(props.apiPath, {
      page: page.value,
      size: PAGE_SIZE,
    });
    if (run !== sequence) return;
    items.value = data.content;
    total.value = data.total;
  } catch (e) {
    if (run === sequence)
      error.value = (e as ApiError).message || '加载失败，请稍后重试';
  } finally {
    if (run === sequence) loading.value = false;
  }
}

function onPageChange(next: number) {
  void setPage(next);
}

function goDetail(id: number) {
  router.push({ name: 'aftersale-detail', params: { id } });
}

watch(() => [props.apiPath, page.value], load, { immediate: true });
onScopeDispose(() => {
  sequence++;
});
</script>

<style scoped>
.mm-aftersale-list {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-3);
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: var(--mm-space-5) var(--mm-space-4);
}

.mm-aftersale-list__error {
  color: var(--mm-danger);
}

.mm-aftersale-list__hint {
  color: var(--mm-muted);
  text-align: center;
  padding: var(--mm-space-6) 0;
}

.mm-aftersale-list__row {
  display: flex;
  justify-content: space-between;
  gap: var(--mm-space-4);
}

.mm-aftersale-list__title-line {
  display: flex;
  align-items: center;
  gap: var(--mm-space-2);
  margin-bottom: var(--mm-space-1);
}

.mm-aftersale-list__type {
  font-weight: 600;
}

.mm-aftersale-list__meta {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-aftersale-list__amounts {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--mm-space-1);
  font-size: var(--mm-font-s);
  white-space: nowrap;
}

.mm-aftersale-list__amounts .mm-button {
  margin-top: var(--mm-space-1);
}

@media (max-width: 768px) {
  .mm-aftersale-list__row {
    flex-direction: column;
  }

  .mm-aftersale-list__amounts {
    flex-direction: row;
    align-items: center;
    gap: var(--mm-space-3);
  }

  .mm-aftersale-list__amounts .mm-button {
    margin-top: 0;
    margin-left: auto;
  }
}
</style>
