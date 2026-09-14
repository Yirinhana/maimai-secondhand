<template>
  <div class="mm-detail">
    <p v-if="loading" class="mm-detail__loading">加载中……</p>
    <div v-else-if="error" class="mm-detail__error" role="alert">
      <EmptyState title="商品不存在或已下架" :description="error" />
    </div>

    <template v-else-if="product">
      <p v-if="product.status !== 'ON_SALE'" class="mm-detail__status-banner" role="alert">
        该商品当前{{ PRODUCT_STATUS_TEXT[product.status] }}，暂不可购买
      </p>

      <div class="mm-detail__layout">
        <!-- 图片画廊 -->
        <section class="mm-gallery" aria-label="商品图片">
          <div class="mm-gallery__main">
            <ItemImage
              v-if="currentImage"
              :src="currentImage.path"
              :alt="`${product.title} 图片 ${currentIndex + 1}`"
            />
            <span v-else class="mm-gallery__no-image">暂无图片</span>
          </div>
          <ul v-if="product.images.length > 1" class="mm-gallery__thumbs">
            <li v-for="(img, i) in product.images" :key="img.id">
              <button
                class="mm-gallery__thumb"
                :class="{ 'is-active': i === currentIndex }"
                :aria-label="`查看第 ${i + 1} 张图片`"
                @click="currentIndex = i"
              >
                <ItemImage :src="img.path" :alt="`${product.title} 缩略图 ${i + 1}`" loading="lazy" />
              </button>
            </li>
          </ul>
        </section>

        <!-- 信息与操作区 -->
        <section class="mm-detail__info">
          <h1 class="mm-detail__title">{{ product.title }}</h1>
          <div class="mm-detail__meta">
            <MmTag :text="CONDITION_TEXT[product.condition]" tone="primary" />
            <span class="mm-detail__region">{{ product.region }}</span>
            <span class="mm-detail__time">发布于 {{ formatTime(product.createdAt) }}</span>
          </div>

          <div class="mm-detail__price-row">
            <PriceText class="mm-detail__price" :cents="product.priceCents" />
            <span class="mm-detail__stock">
              <template v-if="product.stockAvailable > 0">库存 {{ product.stockAvailable }} 件</template>
              <template v-else>已售罄</template>
            </span>
          </div>

          <!-- 缺陷与成色显著展示，不隐藏 -->
          <div class="mm-detail__defects" :class="{ 'mm-detail__defects--none': !product.defects }">
            <h2 class="mm-detail__block-title">成色与缺陷说明</h2>
            <p v-if="product.defects">{{ product.defects }}</p>
            <p v-else>卖家未填写缺陷说明，请结合图片与描述判断，必要时先与卖家议价沟通。</p>
          </div>

          <!-- 运费与交付方式明示 -->
          <div class="mm-detail__delivery">
            <h2 class="mm-detail__block-title">交付与运费</h2>
            <ul>
              <li v-for="m in product.deliveryMethods" :key="m">
                <template v-if="m === 'EXPRESS'">
                  快递：运费 {{ product.freightCents > 0 ? formatPrice(product.freightCents) : '免运费' }}（下单时一并结算）
                </template>
                <template v-else>面交：与卖家约定时间地点，当面交付</template>
              </li>
            </ul>
            <p v-if="product.deliveryMethods.includes('EXPRESS')">快递配送范围：{{product.shippingProvinces?.length?product.shippingProvinces.join('、'):'全国'}}</p>
          </div>

          <div v-if="product.returnPromise" class="mm-detail__return">
            <h2 class="mm-detail__block-title">退货承诺</h2>
            <p>{{ product.returnPromise }}</p>
          </div>

          <!-- 操作区 -->
          <div v-if="product.status === 'ON_SALE'" class="mm-detail__actions">
            <div class="mm-detail__controls">
              <label class="mm-detail__control">
                <span>数量</span>
                <input
                  v-model.number="quantity"
                  type="number"
                  min="1"
                  :max="product.stockAvailable"
                  :disabled="product.stockAvailable <= 0"
                />
              </label>
              <label v-if="product.deliveryMethods.length > 1" class="mm-detail__control">
                <span>交付方式</span>
                <select v-model="deliveryMethod">
                  <option v-for="m in product.deliveryMethods" :key="m" :value="m">
                    {{ DELIVERY_METHOD_TEXT[m] }}
                  </option>
                </select>
              </label>
            </div>
            <p v-if="actionMessage" class="mm-detail__action-message" role="status">{{ actionMessage }}</p>
            <p v-if="actionError" class="mm-detail__action-error" role="alert">{{ actionError }}</p>
            <div class="mm-detail__buttons">
              <MmButton :disabled="product.stockAvailable <= 0" @click="buyNow">立即购买</MmButton>
              <MmButton
                variant="ghost"
                :disabled="product.stockAvailable <= 0"
                :loading="cartLoading"
                @click="addToCart"
              >
                加入购物车
              </MmButton>
              <MmButton variant="ghost" @click="bargainOpen = true">议价</MmButton>
            </div>
          </div>
        </section>
      </div>

      <ProductSocialActions :key="product.id" :product-id="product.id" :seller-id="product.seller.id" />
      <!-- 商品描述 -->
      <MmCard title="商品描述" class="mm-detail__section">
        <p class="mm-detail__description">{{ product.description || '卖家还没有填写描述。' }}</p>
      </MmCard>

      <!-- 卖家卡片 -->
      <MmCard class="mm-detail__section">
        <div class="mm-seller">
          <div>
            <p class="mm-seller__name">{{ product.seller.nickname }}</p>
            <p class="mm-seller__hint">点击查看 ta 的更多在售商品</p>
          </div>
          <RouterLink class="mm-seller__link" :to="`/sellers/${product.seller.id}`">
            查看卖家主页
          </RouterLink>
        </div>
      </MmCard>
    </template>

    <!-- 议价对话框 -->
    <div v-if="bargainOpen" class="mm-dialog-mask" @click.self="bargainOpen = false">
      <div class="mm-dialog" role="dialog" aria-modal="true" aria-labelledby="mm-bargain-title">
        <h2 id="mm-bargain-title" class="mm-dialog__title">向卖家议价</h2>
        <p class="mm-dialog__note">议价 24 小时内有效，卖家同意后仅你本人可按议价下单。</p>
        <form @submit.prevent="submitBargain">
          <label class="mm-dialog__field">
            <span>数量</span>
            <input v-model.number="bargainQuantity" type="number" min="1" :max="product?.stockAvailable" required />
          </label>
          <label class="mm-dialog__field">
            <span>你的报价（元/件）</span>
            <input v-model="bargainPrice" type="number" min="0.01" step="0.01" placeholder="输入期望单价" required />
          </label>
          <p v-if="bargainError" class="mm-dialog__error" role="alert">{{ bargainError }}</p>
          <div class="mm-dialog__actions">
            <MmButton type="submit" :loading="bargainLoading">提交议价</MmButton>
            <MmButton variant="ghost" @click="bargainOpen = false">取消</MmButton>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue'
import { computed, ref, watch } from 'vue'
import ProductSocialActions from '../community/ProductSocialActions.vue'
import { useRoute, useRouter } from 'vue-router'
import { get, post, type ApiError } from '../../shared/api'
import EmptyState from '../../shared/components/EmptyState.vue'
import MmButton from '../../shared/components/MmButton.vue'
import MmCard from '../../shared/components/MmCard.vue'
import MmTag from '../../shared/components/MmTag.vue'
import PriceText from '../../shared/components/PriceText.vue'
import { formatPrice, formatTime } from '../../shared/format'
import {
  CONDITION_TEXT,
  DELIVERY_METHOD_TEXT,
  PRODUCT_STATUS_TEXT,
  type CheckoutItem,
  type DeliveryMethod,
  type ProductDetail,
} from '../../shared/types'

const route = useRoute()
const router = useRouter()

const product = ref<ProductDetail | null>(null)
const loading = ref(true)
const error = ref('')
const currentIndex = ref(0)

const quantity = ref(1)
const deliveryMethod = ref<DeliveryMethod>('EXPRESS')
const actionMessage = ref('')
const actionError = ref('')
const cartLoading = ref(false)

const bargainOpen = ref(false)
const bargainQuantity = ref(1)
const bargainPrice = ref('')
const bargainLoading = ref(false)
const bargainError = ref('')

const currentImage = computed(() => product.value?.images[currentIndex.value] ?? null)

async function load(id: string) {
  loading.value = true
  error.value = ''
  product.value = null
  currentIndex.value = 0
  try {
    const data = await get<ProductDetail>(`/products/${id}`)
    product.value = data
    quantity.value = data.stockAvailable > 0 ? 1 : 0
    deliveryMethod.value = data.deliveryMethods[0] ?? 'EXPRESS'
  } catch (e) {
    error.value = (e as ApiError).message || '加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.id,
  (id) => {
    if (typeof id === 'string') load(id)
  },
  { immediate: true },
)

function validQuantity(): number {
  if (!product.value) return 1
  const q = Math.floor(quantity.value)
  if (!Number.isFinite(q) || q < 1) return 1
  return Math.min(q, Math.max(product.value.stockAvailable, 1))
}

function buyNow() {
  if (!product.value) return
  const items: CheckoutItem[] = [
    { productId: product.value.id, quantity: validQuantity(), deliveryMethod: deliveryMethod.value },
  ]
  router.push({ path: '/checkout', query: { items: JSON.stringify(items) } })
}

async function addToCart() {
  if (!product.value) return
  cartLoading.value = true
  actionMessage.value = ''
  actionError.value = ''
  try {
    await post('/cart', {
      productId: product.value.id,
      quantity: validQuantity(),
      deliveryMethod: deliveryMethod.value,
    })
    actionMessage.value = '已加入购物车'
  } catch (e) {
    actionError.value = (e as ApiError).message || '加入购物车失败'
  } finally {
    cartLoading.value = false
  }
}

async function submitBargain() {
  if (!product.value) return
  const price = Number(bargainPrice.value)
  if (!Number.isFinite(price) || price <= 0) {
    bargainError.value = '请输入有效报价'
    return
  }
  const q = Math.floor(bargainQuantity.value)
  if (!Number.isFinite(q) || q < 1) {
    bargainError.value = '请输入有效数量'
    return
  }
  bargainLoading.value = true
  bargainError.value = ''
  try {
    await post(`/products/${product.value.id}/bargains`, {
      quantity: q,
      offerPriceCents: Math.round(price * 100),
    })
    bargainOpen.value = false
    actionMessage.value = '议价已提交，24 小时内有效，可在「我的议价」查看进度'
    actionError.value = ''
  } catch (e) {
    bargainError.value = (e as ApiError).message || '议价提交失败'
  } finally {
    bargainLoading.value = false
  }
}
</script>

<style scoped>
.mm-detail {
  max-width: 1440px;
  margin: 0 auto;
  padding: var(--mm-space-4) var(--mm-space-4) var(--mm-space-6);
}

.mm-detail__loading {
  color: var(--mm-muted);
  text-align: center;
  padding: var(--mm-space-6) 0;
}

.mm-detail__status-banner {
  background-color: #FBF2E4;
  color: var(--mm-warning);
  border: 1px solid var(--mm-warning);
  border-radius: var(--mm-radius-m);
  padding: var(--mm-space-3) var(--mm-space-4);
  margin-bottom: var(--mm-space-4);
  font-weight: 600;
}

.mm-detail__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--mm-space-5);
  margin-bottom: var(--mm-space-5);
}

.mm-gallery__main {
  aspect-ratio: 1;
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-l);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mm-gallery__main img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.mm-gallery__no-image {
  color: var(--mm-muted);
}

.mm-gallery__thumbs {
  display: flex;
  gap: var(--mm-space-2);
  margin-top: var(--mm-space-3);
  overflow-x: auto;
}

.mm-gallery__thumb {
  width: 64px;
  height: 64px;
  padding: 0;
  border: 2px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  overflow: hidden;
  background: none;
  flex-shrink: 0;
}

.mm-gallery__thumb.is-active {
  border-color: var(--mm-primary);
}

.mm-gallery__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mm-detail__title {
  font-size: var(--mm-font-xl);
  margin-bottom: var(--mm-space-2);
}

.mm-detail__meta {
  display: flex;
  align-items: center;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
  margin-bottom: var(--mm-space-4);
}

.mm-detail__region,
.mm-detail__time {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-detail__price-row {
  display: flex;
  align-items: baseline;
  gap: var(--mm-space-3);
  margin-bottom: var(--mm-space-4);
}

.mm-detail__price {
  font-size: var(--mm-font-xxl);
}

.mm-detail__stock {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-detail__block-title {
  font-size: var(--mm-font-base);
  font-weight: 700;
  margin-bottom: var(--mm-space-2);
}

.mm-detail__defects {
  background-color: #FBF2E4;
  border: 1px solid var(--mm-warning);
  border-radius: var(--mm-radius-m);
  padding: var(--mm-space-3) var(--mm-space-4);
  margin-bottom: var(--mm-space-4);
}

.mm-detail__defects--none {
  background-color: var(--mm-canvas);
  border-color: var(--mm-border);
  color: var(--mm-muted);
}

.mm-detail__delivery {
  margin-bottom: var(--mm-space-4);
}

.mm-detail__delivery ul {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
}

.mm-detail__delivery li {
  font-size: var(--mm-font-base);
  padding-left: var(--mm-space-3);
  position: relative;
}

.mm-detail__delivery li::before {
  content: '·';
  position: absolute;
  left: 0;
  color: var(--mm-primary);
  font-weight: 700;
}

.mm-detail__return {
  margin-bottom: var(--mm-space-4);
  color: var(--mm-success);
}

.mm-detail__actions {
  border-top: 1px solid var(--mm-border);
  padding-top: var(--mm-space-4);
}

.mm-detail__controls {
  display: flex;
  gap: var(--mm-space-4);
  flex-wrap: wrap;
  margin-bottom: var(--mm-space-3);
}

.mm-detail__control {
  display: flex;
  align-items: center;
  gap: var(--mm-space-2);
  font-size: var(--mm-font-s);
  font-weight: 600;
}

.mm-detail__control input,
.mm-detail__control select {
  min-height: 40px;
  padding: 0 var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
  background-color: var(--mm-white);
  width: 110px;
}

.mm-detail__buttons {
  display: flex;
  gap: var(--mm-space-3);
  flex-wrap: wrap;
}

.mm-detail__action-message {
  color: var(--mm-success);
  font-size: var(--mm-font-s);
  margin-bottom: var(--mm-space-2);
}

.mm-detail__action-error {
  color: var(--mm-danger);
  font-size: var(--mm-font-s);
  margin-bottom: var(--mm-space-2);
}

.mm-detail__section {
  margin-bottom: var(--mm-space-4);
}

.mm-detail__description {
  white-space: pre-wrap;
}

.mm-seller {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mm-space-4);
}

.mm-seller__name {
  font-weight: 700;
  font-size: var(--mm-font-l);
}

.mm-seller__hint {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-seller__link {
  font-weight: 600;
  white-space: nowrap;
}

.mm-dialog-mask {
  position: fixed;
  inset: 0;
  background-color: rgba(36, 29, 52, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--mm-space-4);
  z-index: 100;
}

.mm-dialog {
  background-color: var(--mm-white);
  border-radius: var(--mm-radius-l);
  padding: var(--mm-space-5);
  width: 100%;
  max-width: 400px;
}

.mm-dialog__title {
  font-size: var(--mm-font-l);
  margin-bottom: var(--mm-space-2);
}

.mm-dialog__note {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
  margin-bottom: var(--mm-space-4);
}

.mm-dialog__field {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
  margin-bottom: var(--mm-space-3);
  font-size: var(--mm-font-s);
  font-weight: 600;
}

.mm-dialog__field input {
  min-height: 40px;
  padding: 0 var(--mm-space-3);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-m);
}

.mm-dialog__error {
  color: var(--mm-danger);
  font-size: var(--mm-font-s);
  margin-bottom: var(--mm-space-3);
}

.mm-dialog__actions {
  display: flex;
  gap: var(--mm-space-3);
}

@media (max-width: 768px) {
  .mm-detail__layout {
    grid-template-columns: 1fr;
    gap: var(--mm-space-4);
  }
}
</style>
