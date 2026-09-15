<template>
  <div class="mm-seller-page">
    <p v-if="sellerLoading" class="mm-seller-page__loading">加载中……</p>
    <div v-else-if="sellerError" role="alert">
      <EmptyState title="卖家不存在" :description="sellerError" />
    </div>

    <template v-else-if="seller">
      <MmCard class="mm-seller-page__profile">
        <div class="mm-seller-page__profile-inner">
          <UserAvatar
            :src="seller.avatarUrl"
            :nickname="seller.nickname"
            :size="64"
          />
          <div>
            <h1 class="mm-seller-page__name">{{ seller.nickname }}</h1>
            <p class="mm-seller-page__identity">
              {{ seller.sellerApproved ? '平台卖家审核已通过' : '用户主页' }}
            </p>
            <p class="mm-seller-page__joined">
              {{ formatTime(seller.joinedAt) }} 加入麦麦二手
            </p>
          </div>
          <p class="mm-seller-page__count">在售 {{ seller.onSaleCount }} 件</p>
        </div>
      </MmCard>

      <SellerSocial
        :key="seller.id"
        :seller-id="seller.id"
        :can-follow="seller.sellerApproved"
      />
      <h2 class="mm-seller-page__heading">在售商品</h2>
      <p v-if="productError" class="mm-seller-page__error" role="alert">
        {{ productError }}
        <MmButton variant="ghost" @click="loadProducts">重试</MmButton>
      </p>
      <p v-else-if="productsLoading" class="mm-seller-page__loading">
        加载中……
      </p>
      <EmptyState
        v-else-if="products.length === 0"
        :title="
          seller.sellerApproved ? '这家小店暂无在售商品' : '该用户暂无在售商品'
        "
      />
      <template v-else>
        <ul class="mm-seller-page__grid">
          <li v-for="product in products" :key="product.id">
            <ProductCard :product="product" />
          </li>
        </ul>
        <MmPagination :page="page" :total-pages="totalPages" @change="goPage" />
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import SellerSocial from '../community/SellerSocial.vue';
import { useRoute } from 'vue-router';
import { get, type ApiError } from '../../shared/api';
import EmptyState from '../../shared/components/EmptyState.vue';
import MmButton from '../../shared/components/MmButton.vue';
import MmCard from '../../shared/components/MmCard.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import ProductCard from '../../shared/components/ProductCard.vue';
import { formatTime } from '../../shared/format';
import type { Page, ProductSummary, SellerProfile } from '../../shared/types';

const PAGE_SIZE = 20;

const route = useRoute();

const seller = ref<SellerProfile | null>(null);
const sellerLoading = ref(true);
const sellerError = ref('');

const products = ref<ProductSummary[]>([]);
const productsLoading = ref(true);
const productError = ref('');
const page = ref(0);
const totalPages = ref(0);

async function loadSeller(id: string) {
  sellerLoading.value = true;
  sellerError.value = '';
  seller.value = null;
  try {
    seller.value = await get<SellerProfile>(`/sellers/${id}`);
  } catch (e) {
    sellerError.value = (e as ApiError).message || '加载失败，请稍后重试';
  } finally {
    sellerLoading.value = false;
  }
}

async function loadProducts() {
  const id = route.params.id;
  if (typeof id !== 'string') return;
  productsLoading.value = true;
  productError.value = '';
  try {
    const data = await get<Page<ProductSummary>>(`/sellers/${id}/products`, {
      page: page.value,
      size: PAGE_SIZE,
    });
    products.value = data.content;
    totalPages.value = data.totalPages;
  } catch (e) {
    productError.value = (e as ApiError).message || '商品加载失败';
  } finally {
    productsLoading.value = false;
  }
}

function goPage(p: number) {
  page.value = p;
  loadProducts();
}

watch(
  () => route.params.id,
  (id) => {
    if (typeof id !== 'string') return;
    page.value = 0;
    loadSeller(id);
    loadProducts();
  },
  { immediate: true },
);
</script>

<style scoped>
.mm-seller-page__identity {
  color: var(--mm-primary);
  font-size: 13px;
  margin: 8px 0;
}
.mm-seller-page {
  max-width: 1440px;
  margin: 0 auto;
  padding: var(--mm-space-4) var(--mm-space-4) var(--mm-space-6);
}

.mm-seller-page__profile {
  margin-bottom: var(--mm-space-5);
}

.mm-seller-page__profile-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--mm-space-4);
}

.mm-seller-page__name {
  font-size: var(--mm-font-xl);
}

.mm-seller-page__joined {
  font-size: var(--mm-font-s);
  color: var(--mm-muted);
}

.mm-seller-page__count {
  font-weight: 700;
  color: var(--mm-primary);
  white-space: nowrap;
}

.mm-seller-page__heading {
  font-size: var(--mm-font-l);
  margin-bottom: var(--mm-space-3);
}

.mm-seller-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: var(--mm-space-4);
}

.mm-seller-page__grid > li {
  min-width: 0;
}

.mm-seller-page__loading {
  color: var(--mm-muted);
  text-align: center;
  padding: var(--mm-space-6) 0;
}

.mm-seller-page__error {
  color: var(--mm-danger);
  text-align: center;
  padding: var(--mm-space-6) 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--mm-space-3);
}

@media (max-width: 768px) {
  .mm-seller-page__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--mm-space-3);
  }
}
</style>
