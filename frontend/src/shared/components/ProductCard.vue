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
      <span v-if="product.stockAvailable <= 0" class="mm-product-card__soldout"
        >已售罄</span
      >
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
      <p
        v-if="
          product.distanceMeters !== null &&
          product.distanceMeters !== undefined
        "
        class="mm-product-card__sub"
      >
        距所选位置约
        {{
          product.distanceMeters < 1000
            ? `${Math.round(product.distanceMeters)} 米`
            : `${(product.distanceMeters / 1000).toFixed(1)} 公里`
        }}
      </p>
    </div>
  </RouterLink>
</template>

<script setup lang="ts">
import ItemImage from './ItemImage.vue';
import { computed } from 'vue';
import MmTag from './MmTag.vue';
import PriceText from './PriceText.vue';
import {
  CONDITION_TEXT,
  DELIVERY_METHOD_TEXT,
  type ProductSummary,
} from '../types';

const props = defineProps<{ product: ProductSummary }>();

const deliveryText = computed(() =>
  props.product.deliveryMethods.map((m) => DELIVERY_METHOD_TEXT[m]).join('/'),
);
</script>

<style scoped>
.mm-product-card {
  display: block;
  color: var(--mm-ink);
  height: 100%;
  min-width: 0;
}
.mm-product-card:hover {
  text-decoration: none;
}
.mm-product-card:hover .mm-product-card__title {
  color: var(--mm-primary);
}
.mm-product-card__cover {
  position: relative;
  aspect-ratio: 1;
  background: #edeee8;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 10px;
  border: 1px solid #e7e7df;
}
.mm-product-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s;
}
.mm-product-card:hover .mm-product-card__cover img {
  transform: scale(1.025);
}
.mm-product-card__no-cover {
  color: var(--mm-muted);
  font-size: 13px;
}
.mm-product-card__soldout {
  position: absolute;
  inset: auto 0 0;
  text-align: center;
  padding: 6px;
  background: #242522b8;
  color: white;
  font-size: 12px;
}
.mm-product-card__info {
  padding: 13px 2px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-wrap: anywhere;
}
.mm-product-card__title {
  font-size: 14px;
  font-weight: 650;
  line-height: 1.55;
  min-height: 43px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.mm-product-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 7px;
}
.mm-product-card__meta :deep(.mm-price) {
  font-size: 19px;
  color: var(--mm-ink);
  letter-spacing: -0.4px;
}
.mm-product-card__meta :deep(.mm-tag) {
  font-size: 10px;
  padding: 2px 6px;
  border: 0;
  font-weight: 500;
  color: #717561;
  background: #eceee5;
}
.mm-product-card__sub,
.mm-product-card__seller {
  font-size: 11px;
  color: var(--mm-muted);
}
.mm-product-card__seller {
  border-top: 1px solid var(--mm-border);
  padding-top: 8px;
  margin-top: 2px;
}
.mm-product-card__seller:empty {
  display: none;
}
@media (prefers-reduced-motion: reduce) {
  .mm-product-card__cover img {
    transition: none;
  }
}
@media (max-width: 760px) {
  .mm-product-card__title {
    font-size: 13px;
    min-height: 40px;
  }
  .mm-product-card__info {
    padding-top: 10px;
  }
  .mm-product-card__meta :deep(.mm-price) {
    font-size: 17px;
  }
  .mm-product-card__meta :deep(.mm-tag) {
    font-size: 10px;
  }
}
</style>
