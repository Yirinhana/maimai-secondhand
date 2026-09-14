<template>
  <div class="mm-admin">
    <header class="mm-admin__side">
      <div class="mm-admin__heading">
        <span>麦麦运营台</span><small>OPERATIONS CONSOLE</small>
      </div>
      <nav class="mm-admin__nav" aria-label="后台导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="mm-admin__link"
        >
          {{ item.label }}
        </RouterLink>
      </nav>
    </header>
    <main class="mm-admin__main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAuthStore } from '../../shared/stores/auth';
const auth = useAuthStore();
const navItems = computed(() =>
  [
    { to: '/admin', label: '总览' },
    { to: '/admin/seller-apps', label: '卖家申请' },
    { to: '/admin/products', label: '商品审核' },
    { to: '/admin/categories', label: '分类维护' },
    { to: '/admin/official', label: '官方内容' },
    { to: '/admin/community', label: '社区与举报' },
    { to: '/admin/aftersales', label: '售后仲裁' },
    { to: '/admin/support', label: '客服工单' },
    { to: '/admin/trade-todos', label: '交易待办' },
    { to: '/admin/finance', label: '资金与对账' },
    { to: '/admin/users', label: '用户管理' },
    { to: '/admin/audit-logs', label: '审计日志' },
  ].filter((item) => {
    const roles = auth.me?.roles ?? [];
    if (roles.includes('SUPER_ADMIN')) return true;
    if (
      [
        '/admin/products',
        '/admin/seller-apps',
        '/admin/categories',
        '/admin/official',
      ].includes(item.to)
    )
      return roles.includes('OPERATOR');
    if (item.to === '/admin/finance') return false;
    if (
      ['/admin/support', '/admin/aftersales', '/admin/trade-todos'].includes(
        item.to,
      )
    )
      return roles.includes('SUPPORT');
    return true;
  }),
);
</script>

<style scoped>
.mm-admin {
  display: flex;
  flex-direction: column;
  gap: 27px;
  max-width: 1280px;
  margin: auto;
  padding: 26px 28px 50px;
  min-width: 0;
}
.mm-admin__side {
  border: 1px solid #d5d8d5;
  background: white;
  border-radius: 6px;
  overflow: hidden;
}
.mm-admin__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
  padding: 15px 20px;
  background: #292d2a;
  color: white;
  font-size: 16px;
  font-weight: 650;
}
.mm-admin__heading small {
  font-size: 10px;
  letter-spacing: 1.8px;
  font-weight: 400;
  color: #bac2bc;
}
.mm-admin__nav {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  padding: 7px 12px;
}
.mm-admin__link {
  display: block;
  font-size: 12px;
  padding: 9px 12px;
  margin: 2px;
  border-radius: 4px;
  color: #606960;
}
.mm-admin__link:hover {
  background: #f0f2ed;
  text-decoration: none;
}
.mm-admin__link.router-link-exact-active {
  background: #e7ece4;
  color: #283822;
  font-weight: 700;
}
.mm-admin__main {
  min-width: 0;
}
.mm-admin__main :deep(.mm-page) {
  padding: 0;
  max-width: none;
}
@media (max-width: 760px) {
  .mm-admin {
    padding: 19px 18px 35px;
  }
  .mm-admin__heading {
    padding: 13px 16px;
  }
  .mm-admin__heading small {
    display: none;
  }
  .mm-admin__nav {
    padding: 6px;
  }
  .mm-admin__link {
    font-size: 11px;
    padding: 8px 10px;
  }
}
</style>
