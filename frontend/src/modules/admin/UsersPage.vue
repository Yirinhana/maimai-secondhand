<template>
  <section class="admin-users mm-stack">
    <header class="mm-page-heading">
      <div>
        <p class="mm-eyebrow">账号与权限</p>
        <h1>用户管理</h1>
        <p>核对账号状态与管理权限，每次变更都会留下记录。</p>
      </div>
    </header>
    <form
      class="admin-users__search"
      @submit.prevent="
        page = 0;
        load();
      "
    >
      <label
        >按邮箱或昵称查询<input
          v-model="keyword"
          maxlength="100"
          placeholder="输入邮箱或昵称" /></label
      ><MmButton type="submit" :disabled="loading">查询账号</MmButton>
    </form>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">正在读取账号…</p>
    <div v-else class="admin-users__list">
      <article v-for="u in items" :key="u.id" class="user-row">
        <div class="user-row__main">
          <div class="user-row__identity">
            <h2>{{ u.nickname }}</h2>
            <p>{{ u.email }}</p>
          </div>
          <div class="user-row__meta">
            <span
              class="user-row__status"
              :class="{ 'is-disabled': u.status !== 'ACTIVE' }"
              >{{ u.status === 'ACTIVE' ? '账号正常' : '账号已停用' }}</span
            >
            <p>注册于 {{ formatTime(u.createdAt) }}</p>
          </div>
          <MmButton
            v-if="auth.me?.roles.includes('SUPER_ADMIN') && u.id !== auth.me.id"
            variant="ghost"
            :disabled="busy"
            @click="changeStatus(u)"
            >{{ u.status === 'ACTIVE' ? '停用账号' : '恢复账号' }}</MmButton
          >
        </div>
        <div class="user-row__access">
          <p class="mm-muted">
            已授予角色：{{
              u.roles.map((role) => roleNames[role] || role).join('、')
            }}
          </p>
          <UserRoleEditor
            v-if="auth.me?.roles.includes('SUPER_ADMIN')"
            class="user-row__roles"
            :user-id="u.id"
            :nickname="u.nickname"
            @updated="load"
          />
        </div>
      </article>
      <div v-if="!items.length && !error" class="admin-users__empty">
        <h2>没有找到匹配的账号</h2>
        <p>检查邮箱或昵称，也可以清空关键词查看全部账号。</p>
        <MmButton
          v-if="keyword"
          variant="ghost"
          @click="
            keyword = '';
            page = 0;
            load();
          "
          >查看全部账号</MmButton
        >
      </div>
    </div>
    <MmPagination
      :page="page"
      :total-pages="pages"
      @change="
        page = $event;
        load();
      "
    />
  </section>
</template>

<script setup lang="ts">
import UserRoleEditor from './UserRoleEditor.vue';
import { askConfirmation } from '../../shared/confirm';
import { ref } from 'vue';
import { useAdminList } from './useAdminList';
import { useAuthStore } from '../../shared/stores/auth';
import { formatTime } from '../../shared/format';
import type { AdminUser } from '../../shared/types';
import MmButton from '../../shared/components/MmButton.vue';
import MmPagination from '../../shared/components/MmPagination.vue';
const auth = useAuthStore(),
  keyword = ref('');
const roleNames: Record<string, string> = {
  USER: '普通用户',
  SELLER: '卖家',
  SUPER_ADMIN: '超级管理员',
  OPERATOR: '运营',
  SUPPORT: '客服',
};
const { items, page, pages, loading, busy, error, load, action } =
  useAdminList<AdminUser>('/admin/users', () => ({ keyword: keyword.value }));
async function changeStatus(u: AdminUser) {
  if (
    !(await askConfirmation(
      `确认${u.status === 'ACTIVE' ? '停用' : '恢复'}「${u.nickname}」？`,
    ))
  )
    return;
  await action(`/admin/users/${u.id}/status`, {
    status: u.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
  });
}
</script>
<style scoped>
.admin-users__search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 16px;
  padding: 18px 20px;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
  background: white;
}
.admin-users__search label {
  display: grid;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}
.admin-users__search input {
  width: 100%;
}
.admin-users__list {
  display: grid;
  gap: 12px;
}
.user-row {
  padding: 20px;
  background: white;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
  min-width: 0;
}
.user-row__main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 0.65fr) auto;
  gap: 20px;
  align-items: center;
}
.user-row__identity {
  min-width: 0;
  overflow-wrap: anywhere;
}
.user-row h2 {
  font-size: 17px;
  margin-bottom: 5px;
}
.user-row__identity p,
.user-row__meta p {
  font-size: 13px;
  color: var(--mm-muted);
}
.user-row__meta {
  display: grid;
  gap: 7px;
  justify-items: start;
}
.user-row__status {
  display: inline-flex;
  padding: 4px 9px;
  border-radius: 6px;
  background: #edf6ef;
  color: #28613c;
  font-size: 12px;
  font-weight: 600;
}
.user-row__status.is-disabled {
  background: #fff1ed;
  color: #9a432d;
}
.user-row__access {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 14px;
  border-top: 1px solid var(--mm-border);
  margin-top: 16px;
  padding-top: 12px;
}
.user-row__access > p {
  padding-top: 10px;
}
.user-row__access:has(.mm-form) {
  grid-template-columns: 1fr;
}
.user-row__roles :deep(.mm-form) {
  max-width: none;
  margin-top: 14px;
  padding: 18px;
  background: var(--mm-zone-soft);
  border-radius: 10px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.user-row__roles :deep(.mm-form > :nth-child(n + 3)) {
  grid-column: 1/-1;
}
.user-row__roles :deep(.mm-form > button) {
  justify-self: start;
}
.admin-users__empty {
  padding: 38px 24px;
  text-align: center;
  display: grid;
  justify-items: center;
  gap: 12px;
  background: white;
  border: 1px solid var(--mm-zone-border);
  border-radius: 12px;
}
.admin-users__empty h2 {
  font-size: 18px;
}
.admin-users__empty p {
  color: var(--mm-muted);
}
@media (max-width: 700px) {
  .user-row__main {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 14px;
  }
  .user-row__identity {
    grid-column: 1/-1;
  }
  .user-row__access {
    grid-template-columns: 1fr;
  }
  .user-row__access > p {
    padding-top: 0;
  }
  .user-row__roles :deep(.mm-form) {
    grid-template-columns: 1fr;
    padding: 14px;
  }
  .user-row {
    padding: 16px;
  }
}
@media (max-width: 420px) {
  .admin-users__search {
    grid-template-columns: 1fr;
    padding: 16px;
  }
  .admin-users__search button {
    justify-self: stretch;
  }
  .user-row__main {
    grid-template-columns: 1fr;
  }
  .user-row__main > button {
    justify-self: start;
  }
}
</style>
