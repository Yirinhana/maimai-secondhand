<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { get } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
import {
  PRODUCT_STATUS_TEXT,
  type Page,
  type SellerProductItem,
} from '../../shared/types';
import UserAvatar from '../../shared/components/UserAvatar.vue';
import ItemImage from '../../shared/components/ItemImage.vue';
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
      <div>
        <p class="mm-eyebrow">我的闲置小店</p>
        <h1>{{ auth.me?.nickname }}的工作台</h1>
        <p class="mm-muted">商品、交易和售后，在这里有序处理。</p>
      </div>
      <RouterLink
        v-if="auth.isSeller"
        :to="`/sellers/${auth.me?.id}`"
        class="mm-text-link"
        >查看我的店铺 →</RouterLink
      >
    </header>
    <div v-if="!auth.isSeller" class="mm-panel mm-stack">
      <h2>开一家自己的闲置小店</h2>
      <p>先在个人中心填写卖家资料，审核通过后即可管理商品。</p>
      <RouterLink to="/me" class="mm-text-link">前往个人中心 →</RouterLink>
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
        <section class="mm-panel mm-stack">
          <h2>最近发布</h2>
          <p v-if="!loading && !error && !recent.length" class="mm-muted">
            还没有商品，整理一下闲置，开始发布吧。
          </p>
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
        <aside class="mm-panel mm-stack">
          <h2>经营提醒</h2>
          <p>发布前检查成色、瑕疵和配件，商品数量请与手头库存保持一致。</p>
          <RouterLink to="/seller/orders">查看订单与交付 →</RouterLink
          ><RouterLink to="/seller/aftersales">查看售后申请 →</RouterLink
          ><RouterLink to="/messages">回复买家私信 →</RouterLink>
          <hr />
          <p class="mm-muted">
            体验商品的供给和成交记录会标注来源；支付渠道资格以实际开通情况为准。
          </p>
        </aside>
      </div>
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
.seller-home__heading > div {
  flex: 1;
  min-width: 0;
}
.seller-home__heading h1 {
  overflow-wrap: anywhere;
}
.seller-home__heading > a {
  margin-left: auto;
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
  grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr);
  gap: 22px;
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
@media (max-width: 760px) {
  .seller-home__heading {
    flex-wrap: wrap;
  }
  .seller-home__heading > a {
    margin-left: 76px;
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
</style>
