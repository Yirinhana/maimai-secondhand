<template>
  <section class="mm-stack"><h1>卖家申请审核</h1><label>状态 <select v-model="status" @change="page=0;load()"><option value="">全部</option><option v-for="(text,key) in SELLER_STATUS_TEXT" :key="key" :value="key">{{text}}</option></select></label><p v-if="error" class="mm-error" role="alert">{{error}}</p><p v-if="loading">加载中…</p><article v-for="item in items" :key="item.id" class="mm-panel"><h2>{{item.nickname}}</h2><p class="mm-muted">{{item.email}} · {{formatTime(item.createdAt)}}</p><p>{{item.intro||'未填写介绍'}}</p><p>平台审核：{{SELLER_STATUS_TEXT[item.status]}} · 渠道资格：{{CHANNEL_STATUS_TEXT[item.channelStatus]}}</p><p v-if="item.reason">上次审核说明：{{item.reason}}</p><form v-if="canReview" class="mm-form" @submit.prevent="review(item.id)"><label>处理结果<select v-model="decision[item.id]"><option value="APPROVE">通过平台审核</option><option value="REJECT">拒绝</option><option value="SUPPLEMENT">要求补充</option><option value="SUSPEND">暂停</option></select></label><label>理由<textarea v-model="reasons[item.id]" required maxlength="500" /></label><p class="mm-muted">真实渠道资格须渠道核验。当前本地测试资格不代表真实收款资质。</p><label style="display:block"><input v-model="qualified[item.id]" type="checkbox" style="width:auto;min-height:auto" /> 标记已核验渠道资格（仅授权测试环境）</label><MmButton type="submit" :disabled="busy">保存审核结果</MmButton></form></article><p v-if="!loading&&!items.length" class="mm-muted">暂无申请</p><MmPagination :page="page" :total-pages="pages" @change="page=$event;load()" /></section>
</template>

<script setup lang="ts">
import {computed,ref} from 'vue'
import {useAuthStore} from '../../shared/stores/auth'
import {useAdminList} from './useAdminList'
import {formatTime} from '../../shared/format'
import {SELLER_STATUS_TEXT,CHANNEL_STATUS_TEXT,type AdminSellerApplication} from '../../shared/types'
import MmButton from '../../shared/components/MmButton.vue'
import MmPagination from '../../shared/components/MmPagination.vue'
const status=ref('PENDING'),decision=ref<Record<number,string>>({}),reasons=ref<Record<number,string>>({}),qualified=ref<Record<number,boolean>>({}),auth=useAuthStore()
const canReview=computed(()=>auth.me?.roles.some(r=>['SUPER_ADMIN','OPERATOR'].includes(r)))
const {items,page,pages,loading,busy,error,load,action}=useAdminList<AdminSellerApplication>('/admin/seller-applications',()=>({status:status.value}))
async function review(id:number){await action(`/admin/seller-applications/${id}/review`,{action:decision.value[id]||'APPROVE',reason:reasons.value[id],channelQualified:qualified.value[id]||false})}
</script>
