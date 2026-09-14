<template>
  <div class="mm-checkout">
    <h1 class="mm-checkout__heading">确认订单</h1>

    <p v-if="error" class="mm-checkout__error" role="alert">{{ error }}</p>
    <p v-if="loading" class="mm-checkout__hint">正在加载结算信息…</p>

    <template v-else-if="lines.length">
      <!-- 收货地址（有快递组时必选） -->
      <MmCard v-if="needsExpress" title="收货地址">
        <p v-if="addressError" class="mm-checkout__error" role="alert">{{ addressError }}</p>
        <ul v-if="addresses.length" class="mm-checkout__addresses">
          <li v-for="addr in addresses" :key="addr.id">
            <label class="mm-checkout__address">
              <input
                v-model="addressId"
                type="radio"
                name="address"
                :value="addr.id"
              />
              <span>
                <strong>{{ addr.receiver }}</strong> {{ addr.phone }}<br />
                {{ addr.region }} {{ addr.detail }}
                <MmTag v-if="addr.isDefault" text="默认" tone="primary" />
              </span>
            </label>
          </li>
        </ul>
        <p v-else class="mm-checkout__hint">还没有收货地址。</p>
        <RouterLink to="/me" class="mm-checkout__link">去账号页管理收货地址 →</RouterLink>
      </MmCard>

      <!-- 面交信息（有面交组时必填） -->
      <MmCard v-if="needsMeetup" title="面交约定">
        <div class="mm-checkout__meetup">
          <MmInput
            v-model="meetupLocation"
            label="面交地点"
            placeholder="例如：学校东门快递柜旁"
            :maxlength="200"
            :error="fieldErrors.meetupLocation"
          />
          <MmInput
            v-model="meetupTime"
            label="面交时间"
            type="datetime-local"
            :error="fieldErrors.meetupTime"
            hint="卖家可在发货前调整面交约定"
          />
          <MapPicker @select="meetupLocation=$event.fullAddress" />
        </div>
      </MmCard>

      <!-- 分组预览 -->
      <MmCard
        v-for="group in previewGroups"
        :key="group.key"
        :title="`卖家：${group.sellerNickname}`"
      >
        <template #extra>
          <MmTag :text="DELIVERY_METHOD_TEXT[group.deliveryMethod]" tone="primary" />
        </template>
        <ul class="mm-checkout__lines">
          <li v-for="line in group.lines" :key="line.key" class="mm-checkout__line">
            <span class="mm-checkout__line-cover">
              <ItemImage v-if="line.coverImage" :src="line.coverImage" :alt="line.title" />
              <span v-else class="mm-checkout__line-noimg">暂无图</span>
            </span>
            <div class="mm-checkout__line-info">
              <span class="mm-checkout__line-title">{{ line.title }}</span>
              <span v-if="line.bargainId" class="mm-checkout__line-bargain">
                议价成交（议价单 #{{ line.bargainId }}）
              </span>
              <span class="mm-checkout__line-price">
                <PriceText :cents="line.unitPriceCents" /> × {{ line.quantity }}
              </span>
            </div>
            <PriceText :cents="line.unitPriceCents * line.quantity" />
          </li>
        </ul>
        <dl class="mm-checkout__amounts">
          <div><dt>商品款</dt><dd><PriceText :cents="group.goodsCents" /></dd></div>
          <div>
            <dt>运费（取组内最高，多数量不叠加）</dt>
            <dd><PriceText :cents="group.freightCents" /></dd>
          </div>
          <div>
            <dt>卖家承担平台服务费（预估）</dt>
            <dd><PriceText :cents="group.feeCents" /></dd>
          </div>
          <div class="mm-checkout__amounts-total">
            <dt>子订单合计</dt>
            <dd><PriceText :cents="group.goodsCents + group.freightCents" /></dd>
          </div>
        </dl>
      </MmCard>

      <MmCard title="合计">
        <dl class="mm-checkout__amounts">
          <div><dt>商品款</dt><dd><PriceText :cents="totalGoodsCents" /></dd></div>
          <div><dt>运费</dt><dd><PriceText :cents="totalFreightCents" /></dd></div>
          <div>
            <dt>应付合计</dt>
            <dd class="mm-checkout__grand"><PriceText :cents="totalPayCents" /></dd>
          </div>
        </dl>
        <p class="mm-checkout__note">
          金额为前端预览，最终金额、平台费与库存以后端提交时权威复算为准；库存不足将整批失败。
        </p>
        <MmButton :loading="submitting" :disabled="!canSubmit" @click="submit">
          提交订单
        </MmButton>
      </MmCard>
    </template>

    <EmptyState
      v-else
      title="没有待结算的商品"
      description="从购物车勾选商品，或在商品详情页直接购买"
    >
      <RouterLink to="/cart"><MmButton>去购物车</MmButton></RouterLink>
    </EmptyState>
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { get, post, type ApiError } from '../../shared/api'
import {
  DELIVERY_METHOD_TEXT,
  type Address,
  type Bargain,
  type CartItem,
  type CheckoutItem,
  type CheckoutResponse,
  type DeliveryMethod,
  type ProductDetail,
} from '../../shared/types'
import MmButton from '../../shared/components/MmButton.vue'
import MmCard from '../../shared/components/MmCard.vue'
import MmInput from '../../shared/components/MmInput.vue'
import MmTag from '../../shared/components/MmTag.vue'
import EmptyState from '../../shared/components/EmptyState.vue'
import PriceText from '../../shared/components/PriceText.vue'
import MapPicker from '../../shared/components/MapPicker.vue'

/** 结算预览行：购物车勾选或详情直购统一成此结构 */
interface PreviewLine {
  key: string
  productId: number
  title: string
  coverImage: string | null
  unitPriceCents: number
  quantity: number
  deliveryMethod: DeliveryMethod
  sellerId: number
  sellerNickname: string
  freightCents: number
  bargainId?: number
}

interface PreviewGroup {
  key: string
  sellerNickname: string
  deliveryMethod: DeliveryMethod
  lines: PreviewLine[]
  goodsCents: number
  freightCents: number
  feeCents: number
}

/** 直购入口传入的条目（query.items JSON 或 history.state.items） */
interface DirectItem {
  productId: number
  quantity: number
  deliveryMethod: DeliveryMethod
  bargainId?: number
  priceCents?: number
}

const route = useRoute()
const router = useRouter()

const lines = ref<PreviewLine[]>([])
const cartItemIds = ref<number[]>([])
const loading = ref(true)
const error = ref('')

const addresses = ref<Address[]>([])
const addressError = ref('')
const addressId = ref<number | null>(null)
const meetupLocation = ref('')
const meetupTime = ref('')
const fieldErrors = reactive({ meetupLocation: '', meetupTime: '' })

const submitting = ref(false)
/** 幂等键在进入页面时生成一次：网络重试复用同一键，后端幂等返回原批次 */
const idempotencyKey = crypto.randomUUID()

const needsExpress = computed(() => lines.value.some((l) => l.deliveryMethod === 'EXPRESS'))
const needsMeetup = computed(() => lines.value.some((l) => l.deliveryMethod === 'MEETUP'))

const previewGroups = computed<PreviewGroup[]>(() => {
  const map = new Map<string, PreviewGroup>()
  for (const line of lines.value) {
    const key = `${line.sellerId}|${line.deliveryMethod}`
    let group = map.get(key)
    if (!group) {
      group = {
        key,
        sellerNickname: line.sellerNickname,
        deliveryMethod: line.deliveryMethod,
        lines: [],
        goodsCents: 0,
        freightCents: 0,
        feeCents: 0,
      }
      map.set(key, group)
    }
    group.lines.push(line)
    group.goodsCents += line.unitPriceCents * line.quantity
    group.freightCents = Math.max(group.freightCents, line.freightCents)
  }
  // 平台服务费预估：费率万分之三，仅按商品款计（与后端 FeeCalculator 一致）
  for (const group of map.values()) {
    group.feeCents = Math.floor((group.goodsCents * 3 + 5000) / 10000)
  }
  return [...map.values()]
})

const totalGoodsCents = computed(() =>
  previewGroups.value.reduce((s, g) => s + g.goodsCents, 0),
)
const totalFreightCents = computed(() =>
  previewGroups.value.reduce((s, g) => s + g.freightCents, 0),
)
const totalPayCents = computed(() => totalGoodsCents.value + totalFreightCents.value)

const meetupTimeValid = computed(() => {
  if (!needsMeetup.value) return true
  if (!meetupTime.value) return false
  return !Number.isNaN(new Date(meetupTime.value).getTime())
})

const canSubmit = computed(() => {
  if (submitting.value || lines.value.length === 0) return false
  if (needsExpress.value && addressId.value === null) return false
  if (needsMeetup.value && (!meetupLocation.value.trim() || !meetupTimeValid.value)) return false
  return true
})

function parseDirectItems(): DirectItem[] | null {
  const stateItems = (history.state as { items?: DirectItem[] } | null)?.items
  if (Array.isArray(stateItems) && stateItems.length) return stateItems
  const raw = route.query.items
  if (typeof raw === 'string' && raw) {
    try {
      const parsed: unknown = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length) return parsed as DirectItem[]
    } catch {
      /* 落到下面的报错 */
    }
    throw new Error('直购参数格式不正确')
  }
  return null
}

async function loadFromCart(ids: number[]) {
  const cart = await get<CartItem[]>('/cart')
  const wanted = cart.filter((i) => ids.includes(i.id))
  if (wanted.length === 0) throw new Error('勾选的购物车商品不存在或已被移除')
  const invalid = wanted.filter((i) => i.invalid)
  if (invalid.length) throw new Error('勾选商品中包含已失效商品，请返回购物车调整')
  cartItemIds.value = wanted.map((i) => i.id)
  const productMap = await fetchProducts(wanted.map((i) => i.productId))
  lines.value = wanted.map((i) => {
    const p = productMap.get(i.productId)
    return {
      key: `cart-${i.id}`,
      productId: i.productId,
      title: i.title ?? p?.title ?? '商品',
      coverImage: i.coverImage,
      unitPriceCents: i.priceCents,
      quantity: i.quantity,
      deliveryMethod: i.deliveryMethod,
      sellerId: i.sellerId ?? p?.seller.id ?? 0,
      sellerNickname: i.sellerNickname ?? p?.seller.nickname ?? '卖家',
      freightCents: i.deliveryMethod === 'EXPRESS' ? (p?.freightCents ?? 0) : 0,
    }
  })
}

async function loadDirect(directItems: DirectItem[]) {
  if (directItems.length > 100 || directItems.some(i => !i || !Number.isSafeInteger(i.productId) || i.productId <= 0 || !Number.isSafeInteger(i.quantity) || i.quantity <= 0 || !['EXPRESS','MEETUP'].includes(i.deliveryMethod))) {
    throw new Error('购买条目不正确，请从商品详情重新选择')
  }
  const productMap = await fetchProducts(directItems.map((i) => i.productId))
  const bargains = directItems.some(i => i.bargainId) ? await get<Bargain[]>('/me/bargains') : []
  lines.value = directItems.map((i, idx) => {
    const p = productMap.get(i.productId)
    if (!p) throw new Error('部分商品不存在或已下架')
    const bargain = i.bargainId ? bargains.find(b => b.id === i.bargainId && b.productId === i.productId && b.quantity === i.quantity && b.status === 'CONFIRMED' && new Date(b.expiresAt).getTime() > Date.now()) : undefined
    if (i.bargainId && !bargain) throw new Error('议价单无效或已过期，请返回我的议价确认')
    return {
      key: `direct-${idx}-${i.productId}`,
      productId: i.productId,
      title: p.title,
      coverImage: p.images[0]?.path ?? null,
      unitPriceCents: bargain ? (bargain.counterPriceCents ?? bargain.offerPriceCents) : p.priceCents,
      quantity: i.quantity,
      deliveryMethod: i.deliveryMethod,
      sellerId: p.seller.id,
      sellerNickname: p.seller.nickname,
      freightCents: i.deliveryMethod === 'EXPRESS' ? p.freightCents : 0,
      bargainId: i.bargainId,
    }
  })
}

async function fetchProducts(ids: number[]): Promise<Map<number, ProductDetail>> {
  const unique = [...new Set(ids)]
  const results = await Promise.all(
    unique.map(async (id) => {
      try {
        return await get<ProductDetail>(`/products/${id}`)
      } catch {
        return null
      }
    }),
  )
  const map = new Map<number, ProductDetail>()
  results.forEach((p) => {
    if (p) map.set(p.id, p)
  })
  return map
}

async function loadAddresses() {
  if (!needsExpress.value) return
  addressError.value = ''
  try {
    addresses.value = await get<Address[]>('/me/addresses')
    const def = addresses.value.find((a) => a.isDefault) ?? addresses.value[0]
    addressId.value = def ? def.id : null
  } catch (e) {
    addressError.value = (e as ApiError).message
  }
}

async function submit() {
  if (submitting.value) return
  fieldErrors.meetupLocation = ''
  fieldErrors.meetupTime = ''
  if (needsMeetup.value) {
    if (!meetupLocation.value.trim()) fieldErrors.meetupLocation = '请填写面交地点'
    if (!meetupTimeValid.value) fieldErrors.meetupTime = '请选择面交时间'
    if (fieldErrors.meetupLocation || fieldErrors.meetupTime) return
  }
  submitting.value = true
  error.value = ''
  try {
    const items: CheckoutItem[] = lines.value.map((l) => ({
      productId: l.productId,
      quantity: l.quantity,
      deliveryMethod: l.deliveryMethod,
      bargainId: l.bargainId,
    }))
    const res = await post<CheckoutResponse>('/checkout', {
      idempotencyKey,
      items,
      addressId: needsExpress.value ? (addressId.value ?? undefined) : undefined,
      meetupLocation: needsMeetup.value ? meetupLocation.value.trim() : undefined,
      meetupTime: needsMeetup.value
        ? new Date(meetupTime.value).toISOString()
        : undefined,
      removeCartItemIds: cartItemIds.value.length ? cartItemIds.value : undefined,
    })
    if (res.orders.length === 1) {
      router.replace({ name: 'order-detail', params: { orderNo: res.orders[0].orderNo } })
    } else {
      router.replace({ name: 'my-orders' })
    }
  } catch (e) {
    error.value = (e as ApiError).message
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  error.value = ''
  try {
    const direct = parseDirectItems()
    if (direct) {
      await loadDirect(direct)
    } else {
      const raw = route.query.cartItemIds
      const ids =
        typeof raw === 'string' && raw
          ? raw.split(',').map((s) => Number(s)).filter((n) => Number.isInteger(n) && n > 0)
          : []
      if (ids.length === 0) {
        lines.value = []
        return
      }
      await loadFromCart(ids)
    }
    await loadAddresses()
  } catch (e) {
    error.value = e instanceof Error ? e.message : (e as ApiError).message
    lines.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.mm-checkout {
  max-width:1000px;
  margin:0 auto;
  padding:24px 16px;
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-4);
}

.mm-checkout__heading {
  font-size: var(--mm-font-xl);
  font-weight: 700;
}

.mm-checkout__error {
  color: var(--mm-danger);
  font-size: var(--mm-font-s);
}

.mm-checkout__hint {
  color: var(--mm-muted);
  font-size: var(--mm-font-s);
}

.mm-checkout__addresses {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-3);
}

.mm-checkout__address {
  display: flex;
  gap: var(--mm-space-2);
  align-items: flex-start;
  font-size: var(--mm-font-base);
  line-height: 1.5;
}

.mm-checkout__address input {
  margin-top: 4px;
  accent-color: var(--mm-primary);
}

.mm-checkout__link {
  display: inline-block;
  margin-top: var(--mm-space-3);
  color: var(--mm-primary);
  font-size: var(--mm-font-s);
}

.mm-checkout__meetup {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-3);
}

.mm-checkout__lines {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-3);
}

.mm-checkout__line {
  display: flex;
  align-items: center;
  gap: var(--mm-space-3);
}

.mm-checkout__line-cover {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: var(--mm-radius-m);
  overflow: hidden;
  background-color: var(--mm-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mm-checkout__line-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mm-checkout__line-noimg {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-checkout__line-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mm-checkout__line-title {
  font-weight: 600;
}

.mm-checkout__line-bargain {
  font-size: var(--mm-font-s);
  color: var(--mm-primary);
}

.mm-checkout__line-price {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-checkout__amounts {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
  margin-top: var(--mm-space-3);
  border-top: 1px solid var(--mm-border);
  padding-top: var(--mm-space-3);
}

.mm-checkout__amounts > div {
  display: flex;
  justify-content: space-between;
  font-size: var(--mm-font-s);
}

.mm-checkout__amounts dt {
  color: var(--mm-muted);
}

.mm-checkout__amounts-total {
  font-weight: 700;
}

.mm-checkout__grand {
  font-size: var(--mm-font-l);
}

.mm-checkout__note {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  margin: var(--mm-space-3) 0;
}
</style>
