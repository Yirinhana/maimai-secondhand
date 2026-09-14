<template>
  <div class="mm-admin">
    <aside class="mm-admin__side">
      <h2 class="mm-admin__heading">后台管理</h2>
      <nav class="mm-admin__nav" aria-label="后台导航">
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to" class="mm-admin__link">
          {{ item.label }}
        </RouterLink>
      </nav>
    </aside>
    <main class="mm-admin__main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue'
import {useAuthStore} from '../../shared/stores/auth'
const auth=useAuthStore()
const navItems = computed(()=>[
  { to: '/admin', label: '总览' },
  { to: '/admin/seller-apps', label: '卖家申请' },
  { to: '/admin/products', label: '商品审核' },
  { to: '/admin/categories', label: '分类维护' },
  { to: '/admin/community', label: '社区与举报' },
  { to: '/admin/aftersales', label: '售后仲裁' },
  { to: '/admin/support', label: '客服工单' },
  { to: '/admin/trade-todos', label: '交易待办' },
  { to: '/admin/finance', label: '资金与对账' },
  { to: '/admin/users', label: '用户管理' },
  { to: '/admin/audit-logs', label: '审计日志' },
].filter(item=>{
  const roles=auth.me?.roles??[]
  if(roles.includes('SUPER_ADMIN'))return true
  if(['/admin/products','/admin/seller-apps','/admin/categories'].includes(item.to))return roles.includes('OPERATOR')
  if(item.to==='/admin/finance')return false
  if(['/admin/support','/admin/aftersales','/admin/trade-todos'].includes(item.to))return roles.includes('SUPPORT')
  return true
}))
</script>

<style scoped>
.mm-admin {
  display: flex;
  gap: var(--mm-space-5);
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: var(--mm-space-5) var(--mm-space-4);
}

.mm-admin__side {
  flex-shrink: 0;
  width: 180px;
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-l);
  padding: var(--mm-space-4);
  align-self: flex-start;
}

.mm-admin__heading {
  font-size: var(--mm-font-l);
  margin-bottom: var(--mm-space-3);
}

.mm-admin__nav {
  display: flex;
  flex-direction: column;
  gap: var(--mm-space-1);
}

.mm-admin__link {
  display: block;
  padding: var(--mm-space-2) var(--mm-space-3);
  border-radius: var(--mm-radius-m);
  color: var(--mm-ink);
}

.mm-admin__link:hover {
  background-color: var(--mm-canvas);
  text-decoration: none;
}

.mm-admin__link.router-link-exact-active {
  background-color: var(--mm-primary);
  color: var(--mm-white);
}

.mm-admin__main {
  flex: 1;
  min-width: 0;
}

@media (max-width: 768px) {
  .mm-admin {
    flex-direction: column;
  }

  .mm-admin__side {
    width: 100%;
  }

  .mm-admin__nav {
    flex-direction: row;
    flex-wrap: wrap;
  }
}
</style>
