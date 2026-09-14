<template>
  <RouterLink class="mm-product-card" :to="`/products/${product.id}`">
    <div class="mm-product-card__cover">
      <ItemImage
        v-if="product.coverImage"
        :src="product.coverImage"
        :alt="product.title"
        loading="lazy"
      />
      <span v-else class="mm-product-card__no-cover">暂无图片</span>
      <span v-if="product.stockAvailable <= 0" class="mm-product-card__soldout">已售罄</span>
    </div>
    <div class="mm-product-card__info">
      <h3 class="mm-product-card__title">{{ product.title }}</h3>
      <div class="mm-product-card__meta">
        <PriceText :cents="product.priceCents" />
        <MmTag :text="CONDITION_TEXT[product.condition]" tone="primary" />
      </div>
      <p class="mm-product-card__sub">
        {{ product.region }} · {{ deliveryText }}
      </p>
      <p class="mm-product-card__seller">{{ product.sellerNickname }}</p>
      <p v-if="product.distanceMeters!==null&&product.distanceMeters!==undefined" class="mm-product-card__sub">距所选位置约 {{product.distanceMeters<1000?`${Math.round(product.distanceMeters)} 米`:`${(product.distanceMeters/1000).toFixed(1)} 公里`}}</p>
    </div>
  </RouterLink>
</template>

<script setup lang="ts">
import ItemImage from './ItemImage.vue'
import { computed } from 'vue'
import MmTag from './MmTag.vue'
import PriceText from './PriceText.vue'
import { CONDITION_TEXT, DELIVERY_METHOD_TEXT, type ProductSummary } from '../types'

const props = defineProps<{ product: ProductSummary }>()

const deliveryText = computed(() =>
  props.product.deliveryMethods.map((m) => DELIVERY_METHOD_TEXT[m]).join('/'),
)
</script>

<style scoped>
.mm-product-card {
  display: block;
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-l);
  overflow: hidden;
  color: var(--mm-ink);
  height: 100%;
}

.mm-product-card:hover {
  text-decoration: none;
  border-color: var(--mm-primary);
}

.mm-product-card__cover {
  position: relative;
  aspect-ratio: 1;
  background-color: var(--mm-canvas);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mm-product-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mm-product-card__no-cover {
  color: var(--mm-muted);
  font-size: var(--mm-font-s);
}

.mm-product-card__soldout {
  position: absolute;
  inset: auto 0 0 0;
  text-align: center;
  padding: var(--mm-space-1) 0;
  background-color: rgba(36, 29, 52, 0.72);
  color: var(--mm-white);
  font-size: var(--mm-font-s);
}

.mm-product-card__info {
  padding: var(--mm-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-2);
  overflow-wrap: anywhere;
}

.mm-product-card__title {
  font-size: var(--mm-font-base);
  font-weight: 600;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.mm-product-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--mm-space-2);
}

.mm-product-card__sub,
.mm-product-card__seller {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}
</style>
