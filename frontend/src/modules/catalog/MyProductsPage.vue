<template>
  <section class="mm-workspace">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">SELLER WORKSPACE</p>
        <h1>我的商品</h1>
        <p>从发布到售出，在这里管理你的每一件闲置。</p>
      </div>
      <RouterLink to="/publish" class="mm-inventory__publish"
        ><MmIcon name="plus" />发布新闲置</RouterLink
      >
    </header>
    <div class="mm-summary-strip">
      <div>
        <MmIcon name="grid" />
        <span>当前筛选商品</span><strong>{{ total }}</strong>
      </div>
      <div>
        <MmIcon name="box" />
        <span>本页可售库存</span
        ><strong>{{
          items.reduce((sum, item) => sum + item.stockAvailable, 0)
        }}</strong>
      </div>
      <div>
        <MmIcon name="bag" />
        <span>本页已售件数</span
        ><strong>{{
          items.reduce((sum, item) => sum + item.stockSold, 0)
        }}</strong>
      </div>
    </div>
    <div class="mm-workspace__toolbar">
      <label
        >商品状态<select
          v-model="status"
          @change="
            page = 0;
            load();
          "
        >
          <option value="">全部状态</option>
          <option
            v-for="(label, key) in PRODUCT_STATUS_TEXT"
            :key="key"
            :value="key"
          >
            {{ label }}
          </option>
        </select></label
      >
      <p class="mm-muted">关键内容修改后需重新审核</p>
    </div>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-if="loading" class="mm-muted">正在读取商品…</p>
    <div v-else-if="items.length" class="mm-inventory">
      <div class="mm-inventory__heading" aria-hidden="true">
        <span>商品</span><span>库存 / 销量</span><span>上架状态</span
        ><span>管理操作</span>
      </div>
      <article v-for="p in items" :key="p.id" class="mm-inventory__row">
        <div class="mm-inventory__product">
          <ItemImage :src="p.coverImage" :alt="p.title" />
          <div>
            <h2>{{ p.title }}</h2>
            <PriceText :cents="p.priceCents" />
            <p
              v-if="p.reviewReason"
              class="mm-inventory__review"
              :class="{ 'is-rejected': p.status === 'REJECTED' }"
            >
              审核说明：{{ p.reviewReason }}
            </p>
          </div>
        </div>
        <div class="mm-inventory__stock">
          <strong>可售 {{ p.stockAvailable }}</strong
          ><span>预留 {{ p.stockReserved }} · 已售 {{ p.stockSold }}</span>
        </div>
        <MmTag
          :text="PRODUCT_STATUS_TEXT[p.status]"
          :tone="
            p.status === 'ON_SALE'
              ? 'success'
              : p.status === 'REJECTED'
                ? 'danger'
                : 'neutral'
          "
        />
        <div class="mm-inventory__actions">
          <RouterLink :to="`/publish/${p.id}`" class="mm-inventory__edit"
            >编辑商品</RouterLink
          ><RouterLink v-if="p.status === 'ON_SALE'" :to="`/products/${p.id}`"
            >查看商品</RouterLink
          ><MmButton
            v-if="['DRAFT', 'REJECTED', 'OFF_SHELF'].includes(p.status)"
            variant="ghost"
            :disabled="busy"
            @click="act(p.id, 'submit')"
            >提交审核</MmButton
          ><MmButton
            v-if="p.status === 'ON_SALE'"
            variant="ghost"
            :disabled="busy"
            @click="act(p.id, 'off-shelf')"
            >下架</MmButton
          >
        </div>
      </article>
    </div>
    <EmptyState
      v-else
      :title="status ? '当前状态下暂无商品' : '还没有发布商品'"
      :description="
        status
          ? '试试切换到全部状态，查看你的其他闲置。'
          : '在个人中心完成卖家申请后，就可以发布你的第一件闲置。'
      "
    /><MmPagination
      :page="page"
      :total-pages="totalPages"
      @change="
        page = $event;
        load();
      "
    />
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { get, post, type ApiError } from '../../shared/api';
import {
  PRODUCT_STATUS_TEXT,
  type Page,
  type SellerProductItem,
} from '../../shared/types';
import ItemImage from '../../shared/components/ItemImage.vue';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
import EmptyState from '../../shared/components/EmptyState.vue';
import PriceText from '../../shared/components/PriceText.vue';
import MmTag from '../../shared/components/MmTag.vue';
import MmIcon from '../../shared/components/MmIcon.vue';
const items = ref<SellerProductItem[]>([]),
  page = ref(0),
  totalPages = ref(0),
  total = ref(0),
  status = ref(''),
  error = ref(''),
  loading = ref(true),
  busy = ref(false);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const r = await get<Page<SellerProductItem>>('/seller/products', {
      status: status.value,
      page: page.value,
      size: 12,
    });
    items.value = r.content;
    totalPages.value = r.totalPages;
    total.value = r.totalElements;
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    loading.value = false;
  }
}
async function act(id: number, action: string) {
  busy.value = true;
  try {
    await post(`/seller/products/${id}/${action}`);
    await load();
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<style scoped>
.mm-inventory__publish {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--mm-zone-accent);
  padding: 11px 18px;
  color: white;
  border-radius: 6px;
  font-size: 13px;
  box-shadow: 0 3px 8px #234c4020;
}
.mm-inventory__publish:hover {
  background: #234c40;
  text-decoration: none;
}
.mm-inventory__publish .mm-icon {
  width: 17px;
  height: 17px;
}
.mm-inventory {
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  overflow: hidden;
  background: white;
}
.mm-inventory__heading,
.mm-inventory__row {
  display: grid;
  grid-template-columns:
    minmax(0, 2fr) minmax(110px, 0.7fr) minmax(90px, 0.6fr)
    minmax(150px, 1fr);
  gap: 22px;
  align-items: center;
}
.mm-inventory__heading {
  background: #e8efea;
  padding: 13px 20px;
  font-size: 12px;
  color: #41594a;
  font-weight: 650;
}
.mm-inventory__row {
  padding: 22px 20px;
  border-top: 1px solid var(--mm-border);
}
.mm-inventory__row:hover {
  background: #fcfdfb;
}
.mm-inventory__product {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-width: 0;
}
.mm-inventory__product img {
  width: 78px;
  height: 78px;
  object-fit: cover;
  border-radius: 6px;
  flex: none;
}
.mm-inventory__product > div {
  min-width: 0;
}
.mm-inventory__product h2 {
  font-size: 14px;
  font-weight: 650;
  line-height: 1.6;
  overflow-wrap: anywhere;
  margin: 0 0 8px;
}
.mm-inventory__review {
  font-size: 11px;
  color: var(--mm-muted);
  margin-top: 5px;
  overflow-wrap: anywhere;
}
.mm-inventory__review.is-rejected {
  color: var(--mm-danger);
}
.mm-inventory__stock {
  font-size: 12px;
  line-height: 1.9;
}
.mm-inventory__stock strong {
  display: block;
  font-weight: 600;
}
.mm-inventory__stock span {
  font-size: 11px;
  color: var(--mm-muted);
}
.mm-inventory__row > .mm-tag {
  justify-self: start;
}
.mm-inventory__actions {
  display: flex;
  gap: 10px 15px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 12px;
}
.mm-inventory__actions .mm-button {
  font-size: 12px;
  min-height: 38px;
  padding: 0 10px;
}
.mm-inventory__actions > a {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  color: var(--mm-zone-accent);
}
.mm-inventory__actions > .mm-inventory__edit {
  padding: 0 11px;
  border: 1px solid var(--mm-zone-border);
  background: var(--mm-zone-soft);
  border-radius: 7px;
  font-weight: 650;
}
@media (max-width: 850px) {
  .mm-inventory__heading {
    display: none;
  }
  .mm-inventory__row {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 15px;
    padding: 18px;
  }
  .mm-inventory__product {
    grid-column: 1/-1;
  }
  .mm-inventory__row > .mm-tag {
    grid-column: 2;
    grid-row: 2;
    justify-self: end;
  }
  .mm-inventory__actions {
    grid-column: 1/-1;
    border-top: 1px dashed var(--mm-border);
    padding-top: 12px;
  }
}
</style>
