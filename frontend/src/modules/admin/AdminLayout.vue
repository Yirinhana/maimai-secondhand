<template>
  <div class="mm-admin">
    <header class="mm-admin__navigation">
      <div class="mm-admin__heading">
        <span><MmIcon name="grid" />工作导航</span
        ><small>{{
          auth.me?.roles.includes('SUPER_ADMIN') ? '超级管理员' : '平台工作人员'
        }}</small>
      </div>
      <nav class="mm-admin__nav" aria-label="后台导航">
        <div
          v-for="group in navGroups"
          :key="group.label"
          class="mm-admin__group"
        >
          <p>{{ group.label }}</p>
          <div>
            <RouterLink
              v-for="item in group.items"
              :key="item.to"
              :to="item.to"
              class="mm-admin__link"
              >{{ item.label }}</RouterLink
            >
          </div>
        </div>
      </nav>
    </header>
    <div class="mm-admin__main">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useAuthStore } from '../../shared/stores/auth';
import MmIcon from '../../shared/components/MmIcon.vue';
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
const navGroups = computed(() =>
  [
    {
      label: '概览与账号',
      paths: ['/admin', '/admin/users', '/admin/audit-logs'],
    },
    {
      label: '商品与卖家',
      paths: ['/admin/seller-apps', '/admin/products', '/admin/categories'],
    },
    {
      label: '交易与服务',
      paths: [
        '/admin/trade-todos',
        '/admin/aftersales',
        '/admin/support',
        '/admin/finance',
      ],
    },
    { label: '内容与社区', paths: ['/admin/official', '/admin/community'] },
  ]
    .map((group) => ({
      label: group.label,
      items: navItems.value.filter((item) => group.paths.includes(item.to)),
    }))
    .filter((group) => group.items.length),
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
.mm-admin__navigation {
  border: 1px solid #d5d8d5;
  background: white;
  border-radius: 12px;
  overflow: hidden;
}
.mm-admin__heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 15px;
  padding: 13px 20px;
  background: #f8fafc;
  border-bottom: 1px solid var(--mm-zone-border);
  color: #34475a;
  font-size: 14px;
  font-weight: 650;
}
.mm-admin__heading > span {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mm-admin__heading .mm-icon {
  width: 17px;
  height: 17px;
}
.mm-admin__heading small {
  font-size: 11px;
  font-weight: 400;
  color: #566777;
  border: 1px solid #cfd9e3;
  padding: 3px 10px;
  border-radius: 20px;
}
.mm-admin__nav {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 16px 8px;
}
.mm-admin__group {
  padding: 0 12px;
  border-right: 1px solid #e3e8ee;
}
.mm-admin__group:last-child {
  border-right: 0;
}
.mm-admin__group > p {
  font-size: 11px;
  color: #687889;
  padding: 0 8px 6px;
}
.mm-admin__group > div {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}
.mm-admin__link {
  display: block;
  font-size: 12px;
  padding: 9px 8px;
  border-radius: 6px;
  color: #3f5366;
}
.mm-admin__link:hover {
  background: #f0f3f7;
  text-decoration: none;
}
.mm-admin__link.router-link-exact-active {
  background: #354b60;
  color: white;
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
  .mm-admin__nav {
    padding: 12px 4px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px 0;
  }
  .mm-admin__group {
    padding: 0 6px;
  }
  .mm-admin__group:nth-child(2n) {
    border-right: 0;
  }
  .mm-admin__link {
    font-size: 12px;
    padding: 11px 8px;
  }
}
</style>
