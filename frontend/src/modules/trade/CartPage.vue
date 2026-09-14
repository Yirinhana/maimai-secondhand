<template>
  <div class="mm-cart">
    <h1 class="mm-cart__heading">购物车</h1>

    <p v-if="error" class="mm-cart__error" role="alert">{{ error }}</p>
    <p v-else-if="loading" class="mm-cart__hint">加载中…</p>

    <template v-else-if="groups.length">
      <section v-for="group in groups" :key="group.sellerKey" class="mm-cart__group">
        <h2 class="mm-cart__seller">卖家：{{ group.sellerNickname }}</h2>
        <ul class="mm-cart__items">
          <li
            v-for="item in group.items"
            :key="item.id"
            class="mm-cart__item"
            :class="{ 'is-invalid': item.invalid }"
          >
            <label class="mm-cart__check">
              <input
                type="checkbox"
                :checked="selected.has(item.id)"
                :disabled="item.invalid"
                :aria-label="`选择 ${item.title ?? '失效商品'}`"
                @change="toggle(item)"
              />
            </label>
            <RouterLink :to="`/products/${item.productId}`" class="mm-cart__cover">
              <ItemImage v-if="item.coverImage" :src="item.coverImage" :alt="item.title ?? ''" />
              <span v-else class="mm-cart__cover-empty">暂无图</span>
            </RouterLink>
            <div class="mm-cart__info">
              <RouterLink :to="`/products/${item.productId}`" class="mm-cart__title">
                {{ item.title ?? '商品已删除' }}
              </RouterLink>
              <div class="mm-cart__meta">
                <MmTag :text="DELIVERY_METHOD_TEXT[item.deliveryMethod]" tone="primary" />
                <MmTag
                  v-if="item.invalid"
                  :text="invalidReason(item)"
                  tone="danger"
                />
              </div>
              <PriceText :cents="item.priceCents" />
              <div class="mm-cart__row">
                <div class="mm-cart__stepper" aria-label="数量">
                  <button
                    type="button"
                    :disabled="item.quantity <= 1 || busyId === item.id"
                    aria-label="减少数量"
                    @click="changeQuantity(item, item.quantity - 1)"
                  >
                    −
                  </button>
                  <span class="mm-cart__qty">{{ item.quantity }}</span>
                  <button
                    type="button"
                    :disabled="atMax(item) || busyId === item.id"
                    aria-label="增加数量"
                    @click="changeQuantity(item, item.quantity + 1)"
                  >
                    ＋
                  </button>
                </div>
                <span v-if="item.stockAvailable !== null" class="mm-cart__stock">
                  库存 {{ item.stockAvailable }}
                </span>
                <button
                  type="button"
                  class="mm-cart__remove"
                  :disabled="busyId === item.id"
                  @click="remove(item)"
                >
                  删除
                </button>
              </div>
              <p v-if="item.invalid" class="mm-cart__invalid-hint">
                该商品已失效，不可勾选结算，可删除后重新选购。
              </p>
            </div>
          </li>
        </ul>
      </section>

      <div class="mm-cart__bar">
        <label class="mm-cart__check-all">
          <input
            type="checkbox"
            :checked="allValidSelected"
            :disabled="validItems.length === 0"
            @change="toggleAll"
          />
          全选（不含失效）
        </label>
        <div class="mm-cart__summary">
          <span>
            已选 {{ selectedItems.length }} 件，商品款合计
            <PriceText :cents="selectedGoodsCents" />
          </span>
          <span class="mm-cart__freight-note">运费与平台费在结算页按分组核算</span>
        </div>
        <MmButton :disabled="selectedItems.length === 0" @click="goCheckout">
          去结算
        </MmButton>
      </div>
    </template>

    <EmptyState
      v-else
      title="购物车是空的"
      description="去首页逛逛，把喜欢的闲置加进来吧"
    >
      <RouterLink to="/"><MmButton>去逛逛</MmButton></RouterLink>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { del, get, put, type ApiError } from '../../shared/api'
import {
  DELIVERY_METHOD_TEXT,
  PRODUCT_STATUS_TEXT,
  type CartItem,
} from '../../shared/types'
import MmButton from '../../shared/components/MmButton.vue'
import MmTag from '../../shared/components/MmTag.vue'
import EmptyState from '../../shared/components/EmptyState.vue'
import PriceText from '../../shared/components/PriceText.vue'

interface SellerGroup {
  sellerKey: string
  sellerNickname: string
  items: CartItem[]
}

const router = useRouter()
const items = ref<CartItem[]>([])
const loading = ref(true)
const error = ref('')
const busyId = ref<number | null>(null)
const selected = ref<Set<number>>(new Set())

const groups = computed<SellerGroup[]>(() => {
  const map = new Map<string, SellerGroup>()
  for (const item of items.value) {
    const key = item.sellerId === null ? 'deleted' : String(item.sellerId)
    let group = map.get(key)
    if (!group) {
      group = {
        sellerKey: key,
        sellerNickname: item.sellerNickname ?? '商品已删除',
        items: [],
      }
      map.set(key, group)
    }
    group.items.push(item)
  }
  return [...map.values()]
})

const validItems = computed(() => items.value.filter((i) => !i.invalid))
const selectedItems = computed(() =>
  items.value.filter((i) => !i.invalid && selected.value.has(i.id)),
)
const selectedGoodsCents = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + i.priceCents * i.quantity, 0),
)
const allValidSelected = computed(
  () =>
    validItems.value.length > 0 &&
    validItems.value.every((i) => selected.value.has(i.id)),
)

function invalidReason(item: CartItem): string {
  if (item.title === null || item.productStatus === null) return '商品已删除'
  if (item.productStatus !== 'ON_SALE') {
    return PRODUCT_STATUS_TEXT[item.productStatus] ?? '商品不可售'
  }
  return '库存不足'
}

function atMax(item: CartItem): boolean {
  return item.stockAvailable !== null && item.quantity >= item.stockAvailable
}

function toggle(item: CartItem) {
  if (item.invalid) return
  const next = new Set(selected.value)
  if (next.has(item.id)) next.delete(item.id)
  else next.add(item.id)
  selected.value = next
}

function toggleAll() {
  const next = new Set(selected.value)
  if (allValidSelected.value) {
    for (const i of validItems.value) next.delete(i.id)
  } else {
    for (const i of validItems.value) next.add(i.id)
  }
  selected.value = next
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await get<CartItem[]>('/cart')
    const alive = new Set(items.value.filter((i) => !i.invalid).map((i) => i.id))
    selected.value = new Set([...selected.value].filter((id) => alive.has(id)))
  } catch (e) {
    error.value = (e as ApiError).message
  } finally {
    loading.value = false
  }
}

async function changeQuantity(item: CartItem, quantity: number) {
  if (quantity < 1 || busyId.value !== null) return
  busyId.value = item.id
  error.value = ''
  try {
    const updated = await put<CartItem>(`/cart/${item.id}`, { quantity })
    Object.assign(item, updated)
  } catch (e) {
    error.value = (e as ApiError).message
    await load()
  } finally {
    busyId.value = null
  }
}

async function remove(item: CartItem) {
  if (busyId.value !== null) return
  busyId.value = item.id
  error.value = ''
  try {
    await del(`/cart/${item.id}`)
    items.value = items.value.filter((i) => i.id !== item.id)
    const next = new Set(selected.value)
    next.delete(item.id)
    selected.value = next
  } catch (e) {
    error.value = (e as ApiError).message
  } finally {
    busyId.value = null
  }
}

function goCheckout() {
  const ids = selectedItems.value.map((i) => i.id)
  router.push({ name: 'checkout', query: { cartItemIds: ids.join(',') } })
}

onMounted(load)
</script>

<style scoped>
.mm-cart {
  max-width:1080px;
  margin:0 auto;
  padding:24px 16px;
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
}

.mm-cart__heading {
  font-size: var(--mm-font-xl);
  font-weight: 700;
}

.mm-cart__error {
  color: var(--mm-danger);
  font-size: var(--mm-font-s);
}

.mm-cart__hint {
  color: var(--mm-muted);
  font-size: var(--mm-font-s);
}

.mm-cart__group {
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-l);
  box-shadow: var(--mm-shadow);
  padding: var(--mm-space-4);
}

.mm-cart__seller {
  font-size: var(--mm-font-base);
  font-weight: 700;
  margin-bottom: var(--mm-space-3);
}

.mm-cart__items {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
}

.mm-cart__item {
  display: flex;
  gap: var(--mm-space-3);
  align-items: flex-start;
}

.mm-cart__item.is-invalid {
  opacity: 0.65;
}

.mm-cart__check {
  padding-top: var(--mm-space-2);
}

.mm-cart__check input,
.mm-cart__check-all input {
  width: 18px;
  height: 18px;
  accent-color: var(--mm-primary);
}

.mm-cart__cover {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: var(--mm-radius-m);
  overflow: hidden;
  background-color: var(--mm-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mm-cart__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mm-cart__cover-empty {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-cart__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
  min-width: 0;
}

.mm-cart__title {
  font-weight: 600;
  color: var(--mm-ink);
}

.mm-cart__meta {
  display: flex;
  gap: var(--mm-space-2);
  flex-wrap: wrap;
}

.mm-cart__row {
  display: flex;
  align-items: center;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
}

.mm-cart__stepper {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  overflow: hidden;
}

.mm-cart__stepper button {
  width: 32px;
  height: 32px;
  font-size: var(--mm-font-base);
  color: var(--mm-primary);
}

.mm-cart__stepper button:disabled {
  color: var(--mm-muted);
  cursor: not-allowed;
}

.mm-cart__qty {
  min-width: 36px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.mm-cart__stock {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-cart__remove {
  color: var(--mm-danger);
  font-size: var(--mm-font-s);
  margin-left: auto;
}

.mm-cart__invalid-hint {
  font-size: var(--mm-font-s);
  color: var(--mm-danger);
}

.mm-cart__bar {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-l);
  box-shadow: var(--mm-shadow);
  padding: var(--mm-space-3) var(--mm-space-4);
}

.mm-cart__check-all {
  display: flex;
  align-items: center;
  gap: var(--mm-space-2);
  font-size: var(--mm-font-s);
}

.mm-cart__summary {
  flex: 1;
  display: flex;
  flex-direction: column;
  font-size: var(--mm-font-s);
}

.mm-cart__freight-note {
  color: var(--mm-muted);
}
</style>
