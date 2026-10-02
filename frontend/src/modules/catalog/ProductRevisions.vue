<script setup lang="ts">
import { ref } from 'vue';
import { get, type ApiError } from '../../shared/api';
import { formatTime } from '../../shared/format';
import {
  CONDITION_TEXT,
  PRODUCT_STATUS_TEXT,
  type Page,
  type Condition,
  type ProductStatus,
} from '../../shared/types';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import PriceText from '../../shared/components/PriceText.vue';
import ItemImage from '../../shared/components/ItemImage.vue';
interface Revision {
  version: number;
  actionLabel: string;
  createdAt: string;
  content: {
    title: string;
    categoryName: string;
    description: string;
    condition: Condition;
    defects: string | null;
    priceCents: number;
    region: string;
    freightCents: number;
    returnPromise: string | null;
    shippingProvinces: string[];
    status: ProductStatus;
    reviewReason: string | null;
    images: { path: string }[];
    specifications?: Record<string, string>;
  };
}
const parameterLabels: Record<string, string> = {
  brand: '品牌 / 型号',
  accessories: '随附配件',
  capacity: '容量 / 规格',
  battery: '电池状态',
  repair: '维修记录',
  dimensions: '尺寸',
  disassembly: '拆装情况',
  transport: '搬运条件',
  size: '尺码',
  material: '材质',
  care: '清洁与使用情况',
  edition: '版本 / ISBN',
  annotations: '笔记与缺页',
  usage: '使用情况',
};
const props = defineProps<{ productId: number; admin?: boolean }>(),
  expanded = ref(false),
  items = ref<Revision[]>([]),
  page = ref(0),
  pages = ref(0),
  loading = ref(false),
  error = ref('');
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const result = await get<Page<Revision>>(
      `/${props.admin ? 'admin' : 'seller'}/products/${props.productId}/revisions`,
      { page: page.value, size: 10 },
    );
    items.value = result.content;
    pages.value = result.totalPages;
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}
async function toggle() {
  expanded.value = !expanded.value;
  if (expanded.value) await load();
}
</script>
<template>
  <section class="mm-panel">
    <MmButton variant="ghost" @click="toggle">{{
      expanded ? '收起修改历史' : '查看修改历史'
    }}</MmButton
    ><template v-if="expanded"
      ><p class="mm-muted">
        每次保存的商品信息，方便核对改动。查看记录不会更改当前商品。
      </p>
      <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
      <p v-if="loading">加载中…</p>
      <details v-for="item in items" :key="item.version" class="revision">
        <summary>
          第 {{ item.version }} 次记录 · {{ item.actionLabel }} ·
          {{ formatTime(item.createdAt) }}
        </summary>
        <h3>{{ item.content.title }}</h3>
        <p>分类：{{ item.content.categoryName }}</p>
        <p>
          {{ CONDITION_TEXT[item.content.condition] }} ·
          <PriceText :cents="item.content.priceCents" /> ·
          {{ PRODUCT_STATUS_TEXT[item.content.status] }}
        </p>
        <p style="white-space: pre-wrap">{{ item.content.description }}</p>
        <p>
          缺陷：{{ item.content.defects || '未填写' }} · 地区：{{
            item.content.region
          }}
        </p>
        <p>
          运费 <PriceText :cents="item.content.freightCents" /> · 可配送：{{
            item.content.shippingProvinces?.length
              ? item.content.shippingProvinces.join('、')
              : '全国'
          }}
        </p>
        <p v-for="(value, key) in item.content.specifications" :key="key">
          {{ parameterLabels[key] || '商品参数' }}：{{ value }}
        </p>
        <p>退货承诺：{{ item.content.returnPromise || '未填写' }}</p>
        <p v-if="item.content.reviewReason">
          审核说明：{{ item.content.reviewReason }}
        </p>
        <div class="mm-actions">
          <ItemImage
            v-for="image in item.content.images"
            :key="image.path"
            :src="image.path"
            alt="历史商品图片"
            style="width: 96px; height: 96px; object-fit: contain"
          />
        </div>
      </details>
      <p v-if="!loading && !items.length" class="mm-muted">还没有保存记录。</p>
      <MmPagination
        :page="page"
        :total-pages="pages"
        @change="
          page = $event;
          load();
        "
    /></template>
  </section>
</template>
<style scoped>
.revision {
  padding: 12px 0;
  border-bottom: 1px solid var(--mm-border);
}
.revision summary {
  cursor: pointer;
  color: var(--mm-primary);
}
.revision p {
  margin: 8px 0;
}
</style>
