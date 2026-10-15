<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { get } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import {
  PRODUCT_STATUS_TEXT,
  type Page,
  type SellerProductItem,
} from '../../shared/types';
import SellerTaskList from './SellerTaskList.vue';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import ItemImage from '../../shared/components/ItemImage.vue';
import JourneyGuide from '../../shared/components/JourneyGuide.vue';
const auth = useAuthStore();
const error = ref(''),
  loading = ref(true),
  counts = ref<number[]>([]),
  recent = ref<SellerProductItem[]>([]);
async function load() {
  if (!auth.isSeller) {
    loading.value = false;
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const results = await Promise.all(
      ['', 'ON_SALE', 'PENDING_REVIEW'].map((status) =>
        get<Page<SellerProductItem>>('/seller/products', {
          status,
          size: 4,
          page: 0,
        }),
      ),
    );
    counts.value = results.map((r) => r.totalElements);
    recent.value = results[0]!.content;
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="mm-workspace seller-home">
    <header class="seller-home__heading">
      <UserAvatar
        :src="auth.me?.avatarUrl"
        :nickname="auth.me?.nickname ?? ''"
        :size="60"
      />
      <div class="seller-home__intro">
        <p class="mm-eyebrow">我的闲置小店</p>
        <h1>{{ auth.me?.nickname }}的工作台</h1>
        <p class="mm-muted">商品、交易和售后，在这里有序处理。</p>
      </div>
      <div v-if="auth.isSeller" class="seller-home__actions">
        <RouterLink :to="`/sellers/${auth.me?.id}`" class="seller-home__store"
          >查看店铺</RouterLink
        >
        <RouterLink to="/publish" class="seller-home__publish"
          >＋ 发布闲置</RouterLink
        >
      </div>
    </header>
    <div v-if="!auth.isSeller" class="mm-panel mm-stack">
      <h2>把自己的闲置分享出去</h2>
      <p>
        出售个人闲置也可以申请卖家权限，无需稳定货源。先在个人中心填写资料，平台审核通过后即可管理商品，同一个账号仍然可以购买。
      </p>
      <RouterLink to="/me?tab=seller" class="mm-text-link"
        >查看申请与审核进度 →</RouterLink
      >
      <RouterLink to="/publish" class="mm-text-link"
        >先整理一份闲置草稿 →</RouterLink
      >
    </div>
    <template v-else>
      <p v-if="loading" role="status">正在读取店铺数据…</p>
      <div v-else-if="error" class="mm-error" role="alert">
        {{ error }} <button @click="load">重新加载</button>
      </div>
      <div v-else class="seller-home__stats">
        <RouterLink
          v-for="(label, i) in ['全部商品', '在售商品', '待审核商品']"
          :key="label"
          :to="{
            path: '/seller/products',
            query: {
              status: ['', 'ON_SALE', 'PENDING_REVIEW'][i] || undefined,
            },
          }"
          ><span>{{ label }}</span
          ><strong>{{ counts[i] }}</strong
          ><small>查看商品 →</small></RouterLink
        >
      </div>
      <div class="seller-home__columns">
        <section class="mm-panel mm-stack seller-home__catalog">
          <header class="seller-home__section-heading">
            <div>
              <p class="mm-eyebrow">我的商品</p>
              <h2>最近发布</h2>
            </div>
            <RouterLink to="/seller/products" class="seller-home__manage"
              >管理全部商品 →</RouterLink
            >
          </header>
          <p v-if="!loading && !error && !recent.length" class="mm-muted">
            还没有商品，整理一下闲置，开始发布吧。
          </p>
          <RouterLink
            v-if="!loading && !error && !recent.length"
            to="/publish"
            class="seller-home__publish"
            >发布第一件闲置</RouterLink
          >
          <RouterLink
            v-for="p in recent"
            :key="p.id"
            :to="`/publish/${p.id}`"
            class="seller-home__product"
            ><ItemImage :src="p.coverImage" :alt="p.title" size="thumb" /><span
              ><strong>{{ p.title }}</strong
              ><small
                >{{ PRODUCT_STATUS_TEXT[p.status] }} · 可售
                {{ p.stockAvailable }} 件</small
              ></span
            ><span>编辑 →</span></RouterLink
          >
        </section>
        <aside class="seller-home__service">
          <SellerTaskList />
          <section class="seller-home__reminder">
            <h2>发布前检查一下</h2>
            <p>成色、瑕疵和配件写清楚，库存数量与手头物品保持一致。</p>
            <p class="mm-muted">
              体验商品的供给和成交记录保留来源说明，支付资格以渠道实际开通情况为准。
            </p>
          </section>
        </aside>
      </div>
      <JourneyGuide
        title="卖家使用指南：从发布到完成交付"
        :steps="[
          {
            title: '先保存，再上架',
            body: '还没想好卖什么可先问麦仔。写清使用情况、瑕疵和配件，上传自己的实拍；草稿不会直接上架。',
            to: '/publish',
            action: '整理闲置',
          },
          {
            title: '审核结果在商品管理查看',
            body: '未通过时按审核原因修改。已经上架的商品，关键信息变更会重新审核，待审核期间暂停新成交。',
            to: '/seller/products',
            action: '管理商品',
          },
          {
            title: '确认状态，再安排交付',
            body: '快递订单核对付款后填写运单；面交先确认物品，再核验买家交付码。体验订单不实际寄件。',
            to: '/seller/orders',
            action: '处理订单',
          },
        ]"
      />
    </template>
  </section>
</template>
<style scoped>
.seller-home {
  display: grid;
  gap: 26px;
}
.seller-home__heading {
  display: flex;
  align-items: center;
  gap: 16px;
}
.seller-home__intro {
  flex: 1;
  min-width: 0;
}
.seller-home__heading h1 {
  overflow-wrap: anywhere;
}
.seller-home__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.seller-home__publish,
.seller-home__store {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  min-height: 46px;
  padding: 10px 17px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}
.seller-home__publish {
  background: var(--mm-primary);
  color: white;
  border: 1px solid var(--mm-primary);
}
.seller-home__publish:hover {
  background: var(--mm-primary-hover);
  border-color: var(--mm-primary-hover);
}
.seller-home__store {
  background: white;
  border: 1px solid var(--mm-zone-border);
  color: var(--mm-ink);
}
.seller-home__store:hover {
  border-color: var(--mm-primary);
  background: var(--mm-zone-soft);
}
h1 {
  font-size: 26px;
  margin: 2px 0 6px;
}
h2 {
  font-size: 18px;
}
.seller-home__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.seller-home__stats > a {
  display: grid;
  gap: 10px;
  background: white;
  border: 1px solid var(--mm-border);
  border-top: 3px solid var(--mm-primary);
  border-radius: 10px;
  padding: 22px;
  color: var(--mm-ink);
  text-decoration: none;
}
.seller-home__stats strong {
  font-size: 32px;
}
.seller-home__stats small {
  color: var(--mm-muted);
}
.seller-home__columns {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(320px, 1fr);
  gap: 22px;
  align-items: start;
}
.seller-home__section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--mm-border);
}
.seller-home__section-heading h2 {
  margin-top: 5px;
}
.seller-home__manage {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  font-size: 13px;
  font-weight: 600;
  color: var(--mm-primary);
}
.seller-home__catalog > .seller-home__publish {
  align-self: flex-start;
}
.seller-home__service {
  display: grid;
  gap: 18px;
  min-width: 0;
}
.seller-home__service :deep(.task-counts) {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 10px 0;
  gap: 8px;
}
.seller-home__service :deep(.task-counts a) {
  padding: 12px;
  border: 1px solid var(--mm-border);
}
.seller-home__service :deep(.seller-tasks header) {
  align-items: flex-start;
  flex-wrap: wrap;
}
.seller-home__service :deep(.seller-tasks header > div) {
  flex: 1 1 160px;
}
.seller-home__reminder {
  display: grid;
  gap: 10px;
  padding: 20px;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
  background: var(--mm-zone-soft);
}
.seller-home__reminder p {
  font-size: 13px;
  line-height: 1.8;
}
.seller-home__product {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 0;
  color: var(--mm-ink);
  border-bottom: 1px solid var(--mm-border);
}
.seller-home__product img {
  width: 64px;
  height: 64px;
  border-radius: 6px;
  object-fit: cover;
}
.seller-home__product > span:nth-child(2) {
  flex: 1;
  min-width: 0;
}
.seller-home__product small {
  display: block;
  color: var(--mm-muted);
  font-size: 12px;
  margin-top: 6px;
}
.seller-home__product > span:last-child {
  font-size: 12px;
  color: var(--mm-primary);
}
@media (max-width: 980px) {
  .seller-home__columns {
    grid-template-columns: 1fr;
  }
  .seller-home__heading {
    flex-wrap: wrap;
  }
  .seller-home__actions {
    margin-left: 76px;
  }
  .seller-home__service :deep(.task-counts) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .seller-home__heading {
    display: grid;
    grid-template-columns: 60px minmax(0, 1fr);
    align-items: start;
  }
  .seller-home__actions {
    grid-column: 1/-1;
    margin-left: 0;
    display: grid;
    grid-template-columns: 1fr 1fr;
    width: 100%;
  }
  .seller-home__columns {
    grid-template-columns: minmax(0, 1fr);
  }
  .seller-home__stats {
    gap: 8px;
  }
  .seller-home__stats > a {
    padding: 14px 10px;
    font-size: 12px;
  }
  h1 {
    font-size: 21px;
  }
}
@media (max-width: 550px) {
  .seller-home__service :deep(.task-counts) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .seller-home__stats {
    grid-template-columns: 1fr;
  }
  .seller-home__stats > a {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 2px 12px;
    padding: 12px 16px;
    border-top-width: 1px;
    border-left: 3px solid var(--mm-primary);
  }
  .seller-home__stats strong {
    grid-column: 2;
    grid-row: 1/3;
    font-size: 27px;
    align-self: center;
  }
  .seller-home__stats small {
    grid-column: 1;
  }
}
</style>
