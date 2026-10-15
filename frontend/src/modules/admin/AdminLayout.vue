<template>
  <div class="mm-admin">
    <aside ref="navRoot" class="mm-admin__navigation" tabindex="-1">
      <div class="mm-admin__heading">
        <span><MmIcon name="grid" />工作导航</span
        ><small>{{
          auth.me?.roles.includes('SUPER_ADMIN') ? '超级管理员' : '平台工作人员'
        }}</small>
      </div>
      <button
        ref="navTrigger"
        type="button"
        class="mm-admin__toggle"
        :aria-expanded="navOpen"
        aria-controls="admin-menu"
        @click="navOpen = !navOpen"
      >
        {{ navOpen ? '收起工作导航' : '展开工作导航' }}
      </button>
      <nav
        id="admin-menu"
        :class="{ 'is-open': navOpen }"
        class="mm-admin__nav"
        aria-label="后台导航"
      >
        <section
          v-for="group in navGroups"
          :key="group.label"
          class="mm-admin__group"
          :aria-labelledby="`admin-group-${group.id}`"
        >
          <h2 :id="`admin-group-${group.id}`" class="mm-admin__group-title">
            <MmIcon :name="group.icon" />{{ group.label }}
          </h2>
          <div class="mm-admin__group-links">
            <RouterLink
              v-for="item in group.items"
              :key="item.to"
              :to="item.to"
              class="mm-admin__link"
              @click="navOpen = false"
              ><span>{{ item.label }}</span
              ><MmIcon name="chevron"
            /></RouterLink>
          </div>
        </section>
      </nav>
    </aside>
    <div class="mm-admin__main">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useDismissiblePanel } from '../../shared/useDismissiblePanel';
import { useAuthStore } from '../../shared/stores/auth';
import MmIcon from '../../shared/components/MmIcon.vue';
const auth = useAuthStore();
const navOpen = ref(false);
const navRoot = ref<HTMLElement | null>(null),
  navTrigger = ref<HTMLButtonElement | null>(null);
function closeNavigation(restoreFocus = false) {
  navOpen.value = false;
  if (restoreFocus) navTrigger.value?.focus({ preventScroll: true });
}
useDismissiblePanel(navRoot, () => navOpen.value, closeNavigation);
const route = useRoute();
watch(
  () => route.fullPath,
  () => closeNavigation(),
);
const navItems = computed(() =>
  [
    { to: '/admin', label: '总览' },
    { to: '/admin/seller-apps', label: '卖家申请' },
    { to: '/admin/products', label: '商品审核' },
    { to: '/admin/categories', label: '分类维护' },
    { to: '/admin/official', label: '官方内容' },
    { to: '/admin/community', label: '社区与举报' },
    { to: '/admin/governance', label: '麦仔预审' },
    { to: '/admin/aftersales', label: '售后仲裁' },
    { to: '/admin/support', label: '客服工单' },
    { to: '/admin/orders', label: '订单核查' },
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
    if (item.to === '/admin/community') return roles.includes('OPERATOR');
    if (
      [
        '/admin/support',
        '/admin/aftersales',
        '/admin/trade-todos',
        '/admin/orders',
      ].includes(item.to)
    )
      return roles.includes('SUPPORT');
    return true;
  }),
);
const navGroups = computed(() =>
  [
    {
      id: 'accounts',
      icon: 'user',
      label: '概览与账号',
      paths: ['/admin', '/admin/users', '/admin/audit-logs'],
    },
    {
      id: 'catalog',
      icon: 'box',
      label: '商品与卖家',
      paths: ['/admin/seller-apps', '/admin/products', '/admin/categories'],
    },
    {
      id: 'services',
      icon: 'shield',
      label: '交易与服务',
      paths: [
        '/admin/orders',
        '/admin/trade-todos',
        '/admin/aftersales',
        '/admin/support',
        '/admin/finance',
      ],
    },
    {
      id: 'community',
      icon: 'message',
      label: '内容与社区',
      paths: ['/admin/governance', '/admin/official', '/admin/community'],
    },
  ]
    .map((group) => ({
      id: group.id,
      icon: group.icon,
      label: group.label,
      items: navItems.value.filter((item) => group.paths.includes(item.to)),
    }))
    .filter((group) => group.items.length),
);
</script>

<style scoped>
.mm-admin {
  display: grid;
  grid-template-columns: 205px minmax(0, 1fr);
  align-items: start;
  gap: 28px;
  max-width: 1520px;
  margin: auto;
  padding: 28px 28px 50px;
  min-width: 0;
}
.mm-admin__navigation {
  position: sticky;
  top: 24px;
  display: flex;
  flex-direction: column;
  max-height: calc(100dvh - 48px);
  border: 1px solid var(--mm-zone-border);
  background: white;
  border-radius: 10px;
  overflow: hidden;
}
.mm-admin__heading {
  flex-shrink: 0;
  display: grid;
  gap: 10px;
  padding: 20px;
  background: #263443;
  color: white;
  font-size: 14px;
}
.mm-admin__heading > span {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}
.mm-admin__heading .mm-icon {
  width: 18px;
}
.mm-admin__heading small {
  font-size: 11px;
  color: #c6d4e2;
}
.mm-admin__nav {
  display: grid;
  gap: 20px;
  padding: 14px 10px;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
}
.mm-admin__group {
  min-width: 0;
}
.mm-admin__group-title {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 8px;
  padding: 10px;
  border-bottom: 1px solid #d6e0e8;
  border-radius: 5px 5px 0 0;
  background: #edf1f5;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.4;
  color: #526475;
  letter-spacing: 0.04em;
}
.mm-admin__group-title .mm-icon {
  width: 16px;
  height: 16px;
}
.mm-admin__group-links {
  display: grid;
  gap: 2px;
}
.mm-admin__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 44px;
  font-size: 14px;
  padding: 10px 12px;
  border-radius: 5px;
  color: #3f5366;
}
.mm-admin__link > span {
  min-width: 0;
}
.mm-admin__link > .mm-icon {
  width: 14px;
  height: 14px;
  transform: rotate(-90deg);
  opacity: 0.55;
}
.mm-admin__link:hover {
  background: #f0f3f7;
  text-decoration: none;
}
.mm-admin__link.router-link-exact-active {
  background: #354b60;
  color: white;
  font-weight: 700;
  box-shadow: inset 3px 0 0 #b8cbdc;
}
.mm-admin__main {
  min-width: 0;
}
.mm-admin__main :deep(.mm-page) {
  padding: 0;
  max-width: none;
}
.mm-admin__toggle {
  display: none;
}
@media (max-width: 900px) {
  .mm-admin {
    grid-template-columns: minmax(0, 1fr);
    padding: 18px 16px 32px;
    gap: 20px;
  }
  .mm-admin__navigation {
    position: static;
    max-height: none;
  }
  .mm-admin__heading {
    display: flex;
    justify-content: space-between;
    padding: 12px 16px;
  }
  .mm-admin__toggle {
    display: block;
    width: 100%;
    background: white;
    border: 0;
    padding: 12px;
    color: #354b60;
    text-align: left;
  }
  .mm-admin__nav {
    display: none;
  }
  .mm-admin__nav.is-open {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    max-height: 60dvh;
  }
}
</style>
