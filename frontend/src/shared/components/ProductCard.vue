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
      <span
        v-if="isDemoProductImage(product.coverImage)"
        class="mm-product-card__demo"
        >演示示意图</span
      >
      <span class="mm-product-card__condition">{{
        CONDITION_TEXT[product.condition]
      }}</span>
      <span
        v-if="
          product.stockAvailable <= 0 && !isDemoProductImage(product.coverImage)
        "
        class="mm-product-card__soldout"
        >已售罄</span
      >
    </div>
    <div class="mm-product-card__info">
      <h3 class="mm-product-card__title">{{ product.title }}</h3>
      <div class="mm-product-card__meta">
        <PriceText :cents="product.priceCents" />
        <span
          v-if="product.deliveryMethods.includes('MEETUP')"
          class="mm-product-card__meetup"
          >可面交</span
        >
      </div>
      <p class="mm-product-card__sub">
        <MmIcon name="pin" />{{ product.region
        }}<span class="mm-product-card__delivery">{{ deliveryText }}</span>
      </p>
      <p class="mm-product-card__seller">
        <span class="mm-product-card__seller-mark" aria-hidden="true">{{
          product.sellerNickname?.slice(0, 1) || '麦'
        }}</span
        >{{ product.sellerNickname }}
      </p>
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
import { isDemoProductImage } from '../demoImages';
import MmIcon from './MmIcon.vue';
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
.mm-product-card__condition {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 8px;
  border: 1px solid #ffffff60;
  border-radius: 5px;
  font-size: 11px;
  line-height: 1.5;
  color: #3c4634;
  background: #fbfcf6f2;
}
.mm-product-card__demo {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 2px 6px;
  border-radius: 4px;
  color: #fff;
  background: #242421b8;
  font-size: 10px;
  line-height: 1.6;
}
.mm-product-card__meetup {
  color: #596a49;
  background: #eef1e6;
  padding: 2px 7px;
  border-radius: 4px;
  font-size: 11px;
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
  gap: 9px;
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
.mm-product-card__sub,
.mm-product-card__seller {
  font-size: 12px;
  color: var(--mm-muted);
}
.mm-product-card__sub {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 3px;
}
.mm-product-card__sub .mm-icon {
  width: 13px;
  height: 13px;
}
.mm-product-card__delivery {
  margin-left: auto;
  font-size: 11px;
}
.mm-product-card__seller {
  border-top: 1px solid var(--mm-border);
  padding-top: 8px;
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.mm-product-card__seller-mark {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #ecebe2;
  color: #636852;
  font-size: 10px;
  flex-shrink: 0;
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
  .mm-product-card__condition {
    top: 7px;
    left: 7px;
    padding: 2px 5px;
    font-size: 10px;
  }
  .mm-product-card__delivery {
    display: none;
  }
  .mm-product-card__sub,
  .mm-product-card__seller {
    font-size: 11px;
  }
  .mm-product-card__meta :deep(.mm-price) {
    font-size: 17px;
  }
}
</style>
