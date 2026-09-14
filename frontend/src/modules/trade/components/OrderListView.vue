<template>
  <div class="mm-order-list">
    <nav class="mm-order-list__tabs" aria-label="订单状态筛选">
      <button
        v-for="tab in tabs"
        :key="tab.label"
        type="button"
        class="mm-order-list__tab"
        :class="{ 'is-active': tab.status === activeStatus }"
        @click="switchTab(tab.status)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <p v-if="error" class="mm-order-list__error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="mm-order-list__hint">加载中…</p>

    <template v-else-if="orders.length">
      <RouterLink
        v-for="order in orders"
        :key="order.orderNo"
        :to="{ name: 'order-detail', params: { orderNo: order.orderNo } }"
        class="mm-order-list__card"
      >
        <div class="mm-order-list__card-head">
          <span class="mm-order-list__no">订单号 {{ order.orderNo }}</span>
          <MmTag
            :text="FULFILLMENT_STATUS_TEXT[order.fulfillmentStatus]"
            :tone="statusTone(order.fulfillmentStatus)"
          />
        </div>
        <div class="mm-order-list__peer">
          {{ role === 'buyer' ? `卖家：${order.sellerNickname}` : `买家订单` }}
          · {{ DELIVERY_METHOD_TEXT[order.deliveryMethod] }} · {{ formatTime(order.createdAt) }}
        </div>
        <ul class="mm-order-list__items">
          <li v-for="(item, idx) in order.items" :key="idx" class="mm-order-list__item">
            <span class="mm-order-list__item-cover">
              <ItemImage v-if="item.imagePath" :src="item.imagePath" :alt="item.title" />
              <span v-else class="mm-order-list__item-noimg">暂无图</span>
            </span>
            <span class="mm-order-list__item-title">{{ item.title }}</span>
            <span class="mm-order-list__item-qty">× {{ item.quantity }}</span>
          </li>
        </ul>
        <dl class="mm-order-list__amounts">
          <div><dt>商品款</dt><dd><PriceText :cents="order.goodsAmountCents" /></dd></div>
          <div><dt>运费</dt><dd><PriceText :cents="order.freightCents" /></dd></div>
          <div><dt>卖家承担的平台费</dt><dd><PriceText :cents="order.platformFeeCents" /></dd></div>
          <div class="mm-order-list__total">
            <dt>合计</dt>
            <dd><PriceText :cents="order.totalCents" /></dd>
          </div>
        </dl>
        <p v-if="order.fulfillmentStatus === 'PENDING_PAYMENT'" class="mm-order-list__deadline">
          请在 {{ formatTime(order.expiresAt) }} 前付款，逾期订单自动关闭
        </p>
      </RouterLink>
      <MmPagination :page="page" :total-pages="totalPages" @change="onPage" />
    </template>

    <EmptyState v-else title="暂无相关订单" description="换个状态页签看看，或去首页逛逛" />
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../../shared/components/ItemImage.vue'
import { onMounted, ref } from 'vue'
import { get, type ApiError } from '../../../shared/api'
import { formatTime } from '../../../shared/format'
import {
  DELIVERY_METHOD_TEXT,
  FULFILLMENT_STATUS_TEXT,
  type FulfillmentStatus,
  type OrderDto,
  type Page,
} from '../../../shared/types'
import MmTag from '../../../shared/components/MmTag.vue'
import MmPagination from '../../../shared/components/MmPagination.vue'
import EmptyState from '../../../shared/components/EmptyState.vue'
import PriceText from '../../../shared/components/PriceText.vue'

const props = defineProps<{ role: 'buyer' | 'seller' }>()

const tabs: { label: string; status: FulfillmentStatus | '' }[] = [
  { label: '全部', status: '' },
  { label: '待付款', status: 'PENDING_PAYMENT' },
  { label: '待发货', status: 'PAID_PENDING_SHIP' },
  { label: '待收货', status: 'SHIPPED' },
  { label: '待面交', status: 'AWAITING_MEETUP' },
  { label: '已完成', status: 'COMPLETED' },
  { label: '已关闭', status: 'CLOSED' },
]

const activeStatus = ref<FulfillmentStatus | ''>('')
const orders = ref<OrderDto[]>([])
const page = ref(0)
const totalPages = ref(0)
const loading = ref(true)
const error = ref('')

function statusTone(
  status: FulfillmentStatus,
): 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral' {
  switch (status) {
    case 'PENDING_PAYMENT':
      return 'warning'
    case 'PAID_PENDING_SHIP':
    case 'SHIPPED':
    case 'AWAITING_MEETUP':
      return 'info'
    case 'COMPLETED':
      return 'success'
    case 'CLOSED':
      return 'neutral'
    default:
      return 'primary'
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await get<Page<OrderDto>>('/orders', {
      role: props.role,
      status: activeStatus.value || undefined,
      page: page.value,
      size: 10,
    })
    orders.value = res.content
    totalPages.value = res.totalPages
  } catch (e) {
    error.value = (e as ApiError).message
  } finally {
    loading.value = false
  }
}

function switchTab(status: FulfillmentStatus | '') {
  if (status === activeStatus.value) return
  activeStatus.value = status
  page.value = 0
  load()
}

function onPage(p: number) {
  page.value = p
  load()
}

onMounted(load)
</script>

<style scoped>
.mm-order-list {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
}

.mm-order-list__tabs {
  display: flex;
  gap: var(--mm-space-2);
  overflow-x: auto;
  padding-bottom: var(--mm-space-1);
}

.mm-order-list__tab {
  flex-shrink: 0;
  padding: var(--mm-space-2) var(--mm-space-3);
  border-radius: var(--mm-radius-m);
  border: 1px solid var(--mm-border);
  background-color: var(--mm-white);
  font-size: var(--mm-font-s);
  color: var(--mm-ink);
}

.mm-order-list__tab.is-active {
  background-color: var(--mm-primary);
  border-color: var(--mm-primary);
  color: var(--mm-white);
  font-weight: 600;
}

.mm-order-list__error {
  color: var(--mm-danger);
  font-size: var(--mm-font-s);
}

.mm-order-list__hint {
  color: var(--mm-muted);
  font-size: var(--mm-font-s);
}

.mm-order-list__card {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-l);
  box-shadow: var(--mm-shadow);
  padding: var(--mm-space-4);
  color: var(--mm-ink);
}

.mm-order-list__card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--mm-space-2);
}

.mm-order-list__no {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  font-variant-numeric: tabular-nums;
}

.mm-order-list__peer {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-order-list__items {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
}

.mm-order-list__item {
  display: flex;
  align-items: center;
  gap: var(--mm-space-2);
}

.mm-order-list__item-cover {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: var(--mm-radius-s);
  overflow: hidden;
  background-color: var(--mm-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mm-order-list__item-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mm-order-list__item-noimg {
  font-size: 11px;
  color: var(--mm-muted);
}

.mm-order-list__item-title {
  flex: 1;
  font-size: var(--mm-font-s);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mm-order-list__item-qty {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-order-list__amounts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--mm-space-1) var(--mm-space-4);
  border-top: 1px solid var(--mm-border);
  padding-top: var(--mm-space-2);
}

.mm-order-list__amounts > div {
  display: flex;
  justify-content: space-between;
  font-size: var(--mm-font-s);
}

.mm-order-list__amounts dt {
  color: var(--mm-muted);
}

.mm-order-list__total {
  font-weight: 700;
}

.mm-order-list__deadline {
  font-size: var(--mm-font-s);
  color: var(--mm-warning);
}
</style>
