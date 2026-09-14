<template>
  <section class="mm-admin-overview">
    <header class="mm-admin-overview__head">
      <div>
        <p class="mm-eyebrow">OVERVIEW</p>
        <h1>平台总览</h1>
      </div>
      <span>当前数据库 · 本地环境</span>
    </header>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-else-if="!stats" class="mm-muted">正在读取统计…</p>
    <template v-if="stats"
      ><div class="mm-admin-overview__metrics">
        <div v-for="m in metrics" :key="m.key">
          <span>{{ m.label }}</span
          ><strong>{{ stats[m.key] }}</strong>
        </div>
      </div>
      <div class="mm-admin-overview__finance">
        <div>
          <span>平台服务费累计</span>
          <p>隔离环境中的支付数据仅用于联调。</p>
        </div>
        <PriceText :cents="stats.platformFeeSumCents" /></div
    ></template>
    <section class="mm-admin-overview__queues">
      <header>
        <h2>工作入口</h2>
        <p>按当前角色进入相应处理队列</p>
      </header>
      <nav>
        <RouterLink v-for="queue in queues" :key="queue.to" :to="queue.to"
          ><span
            >{{ queue.label }}<small>{{ queue.description }}</small></span
          ><MmIcon name="arrow"
        /></RouterLink>
      </nav>
    </section>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { get, type ApiError } from '../../shared/api';
import type { AdminStatsOverview } from '../../shared/types';
import { useAuthStore } from '../../shared/stores/auth';
import PriceText from '../../shared/components/PriceText.vue';
import MmIcon from '../../shared/components/MmIcon.vue';
const auth = useAuthStore(),
  stats = ref<AdminStatsOverview | null>(null),
  error = ref('');
const metrics: { key: keyof AdminStatsOverview; label: string }[] = [
  { key: 'userCount', label: '注册用户' },
  { key: 'productOnSaleCount', label: '在售商品' },
  { key: 'orderCount', label: '全部订单' },
  { key: 'paidOrderCount', label: '已支付订单' },
  { key: 'refundSuccessCount', label: '成功退款记录' },
];
const queues = computed(() =>
  [
    {
      to: '/admin/seller-apps',
      label: '卖家准入',
      description: '核对申请资料与经营资格',
      role: 'OPERATOR',
    },
    {
      to: '/admin/products',
      label: '商品审核',
      description: '查看发布内容和修改记录',
      role: 'OPERATOR',
    },
    {
      to: '/admin/official',
      label: '官方内容',
      description: '维护公告、指南和平台介绍',
      role: 'OPERATOR',
    },
    {
      to: '/admin/aftersales',
      label: '售后仲裁',
      description: '依据证据处理交易争议',
      role: 'SUPPORT',
    },
    {
      to: '/admin/support',
      label: '客服工单',
      description: '接管用户问题并持续跟进',
      role: 'SUPPORT',
    },
    {
      to: '/admin/trade-todos',
      label: '交易待办',
      description: '关注逾期发货与交付',
      role: 'SUPPORT',
    },
  ].filter(
    (q) =>
      auth.me?.roles.includes('SUPER_ADMIN') || auth.me?.roles.includes(q.role),
  ),
);
onMounted(async () => {
  try {
    stats.value = await get<AdminStatsOverview>('/admin/stats/overview');
  } catch (e) {
    error.value = (e as ApiError).message;
  }
});
</script>
<style scoped>
.mm-admin-overview {
  display: flex;
  flex-direction: column;
  gap: 23px;
}
.mm-admin-overview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
}
.mm-admin-overview__head h1 {
  margin-top: 7px;
  font-size: 27px;
}
.mm-admin-overview__head > span {
  font-size: 11px;
  color: #66715f;
  border: 1px solid #d8dfd4;
  padding: 6px 10px;
  background: #f8faf5;
}
.mm-admin-overview__metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  background: #fff;
  border: 1px solid #dce0d9;
  border-radius: 5px;
}
.mm-admin-overview__metrics > div {
  padding: 24px;
  border-right: 1px solid #e5e8e1;
}
.mm-admin-overview__metrics > div:last-child {
  border: 0;
}
.mm-admin-overview__metrics span {
  font-size: 11px;
  color: var(--mm-muted);
}
.mm-admin-overview__metrics strong {
  display: block;
  font-size: 33px;
  font-weight: 700;
  margin-top: 7px;
  font-variant-numeric: tabular-nums;
}
.mm-admin-overview__finance {
  padding: 21px 25px;
  background: #e7ebe4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-radius: 4px;
}
.mm-admin-overview__finance span {
  font-size: 13px;
  font-weight: 650;
}
.mm-admin-overview__finance p {
  font-size: 11px;
  color: #77816f;
  margin-top: 4px;
}
.mm-admin-overview__finance :deep(.mm-price) {
  font-size: 24px;
  color: #374d2d;
}
.mm-admin-overview__queues {
  background: white;
  border: 1px solid #dce0d9;
  border-radius: 5px;
}
.mm-admin-overview__queues header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #e5e8e1;
  padding: 18px 24px;
}
.mm-admin-overview__queues h2 {
  font-size: 15px;
}
.mm-admin-overview__queues p {
  font-size: 11px;
  color: var(--mm-muted);
}
.mm-admin-overview__queues nav {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 9px 10px;
}
.mm-admin-overview__queues a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  color: var(--mm-ink);
  padding: 19px 16px;
  font-size: 13px;
  font-weight: 600;
}
.mm-admin-overview__queues a:hover {
  background: #f4f6ef;
  text-decoration: none;
}
.mm-admin-overview__queues small {
  display: block;
  font-size: 10px;
  color: var(--mm-muted);
  font-weight: 400;
  margin-top: 5px;
}
.mm-admin-overview__queues .mm-icon {
  width: 16px;
  height: 16px;
}
@media (max-width: 760px) {
  .mm-admin-overview__head {
    align-items: flex-start;
    flex-direction: column;
  }
  .mm-admin-overview__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .mm-admin-overview__metrics > div {
    padding: 18px;
    border-bottom: 1px solid #e5e8e1;
  }
  .mm-admin-overview__metrics strong {
    font-size: 28px;
  }
  .mm-admin-overview__queues nav {
    grid-template-columns: minmax(0, 1fr);
  }
  .mm-admin-overview__queues header {
    flex-wrap: wrap;
    padding: 17px;
  }
  .mm-admin-overview__finance {
    padding: 19px;
  }
  .mm-admin-overview__finance :deep(.mm-price) {
    font-size: 19px;
  }
}
</style>
