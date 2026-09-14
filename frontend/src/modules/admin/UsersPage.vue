<template>
  <section class="mm-stack"><h1>用户管理</h1><form class="mm-form" @submit.prevent="page=0;load()"><label>按邮箱或昵称查询<input v-model="keyword" maxlength="100" /></label><MmButton type="submit" :disabled="loading">查询</MmButton></form><p v-if="error" class="mm-error" role="alert">{{error}}</p><p v-if="loading">加载中…</p><article v-for="u in items" :key="u.id" class="mm-panel"><h2>{{u.nickname}}</h2><p>{{u.email}} · {{u.status==='ACTIVE'?'正常':'已停用'}}</p><p class="mm-muted">角色：{{u.roles.join('、')}} · 注册 {{formatTime(u.createdAt)}}</p><MmButton v-if="auth.me?.roles.includes('SUPER_ADMIN')&&u.id!==auth.me.id" variant="ghost" :disabled="busy" @click="changeStatus(u)">{{u.status==='ACTIVE'?'停用账号':'恢复账号'}}</MmButton><UserRoleEditor v-if="auth.me?.roles.includes('SUPER_ADMIN')" :user-id="u.id" :nickname="u.nickname" @updated="load" /></article><MmPagination :page="page" :total-pages="pages" @change="page=$event;load()" /></section>
</template>

<script setup lang="ts">
import UserRoleEditor from './UserRoleEditor.vue'
import {askConfirmation} from '../../shared/confirm'
import {ref} from 'vue'
import {useAdminList} from './useAdminList'
import {useAuthStore} from '../../shared/stores/auth'
import {formatTime} from '../../shared/format'
import type {AdminUser} from '../../shared/types'
import MmButton from '../../shared/components/MmButton.vue'
import MmPagination from '../../shared/components/MmPagination.vue'
const auth=useAuthStore(),keyword=ref('')
const {items,page,pages,loading,busy,error,load,action}=useAdminList<AdminUser>('/admin/users',()=>({keyword:keyword.value}))
async function changeStatus(u:AdminUser){if(!await askConfirmation(`确认${u.status==='ACTIVE'?'停用':'恢复'}「${u.nickname}」？`))return;await action(`/admin/users/${u.id}/status`,{status:u.status==='ACTIVE'?'DISABLED':'ACTIVE'})}
</script>
