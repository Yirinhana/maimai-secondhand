<template>
  <section class="mm-stack"><h1>操作审计</h1><p class="mm-muted">敏感操作保留操作者、对象、原因与前后状态。</p><p v-if="error" class="mm-error" role="alert">{{error}}</p><p v-if="loading">加载中…</p><article v-for="item in items" :key="item.id" class="mm-panel"><strong>{{item.action}} · {{item.targetType}} #{{item.targetId}}</strong><p>{{item.reason||'无补充原因'}}</p><p class="mm-muted">管理员 #{{item.adminId}} · {{formatTime(item.createdAt)}}</p><details><summary>变更详情</summary><p style="white-space:pre-wrap;overflow-wrap:anywhere">之前：{{item.beforeState||'空'}}<br />之后：{{item.afterState||'空'}}</p></details></article><p v-if="!loading&&!items.length">暂无日志</p><MmPagination :page="page" :total-pages="pages" @change="page=$event;load()" /></section>
</template>

<script setup lang="ts">
import {useAdminList} from './useAdminList'
import {formatTime} from '../../shared/format'
import type {AuditLog} from '../../shared/types'
import MmPagination from '../../shared/components/MmPagination.vue'
const {items,page,pages,loading,error,load}=useAdminList<AuditLog>('/admin/audit-logs')
</script>
