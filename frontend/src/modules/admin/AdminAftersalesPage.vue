<template>
  <section class="mm-stack"><h1>售后仲裁</h1><p class="mm-notice">处理前查看交易快照、双方意见与证据。退款仅由超级管理员执行，客服可跟进或驳回。</p><label>状态 <select v-model="status" @change="page=0;load()"><option value="">全部</option><option value="PENDING_MANUAL">待人工介入</option><option value="SELLER_REJECTED">卖家已拒绝</option><option value="RESOLVED">已解决</option></select></label><p v-if="error" class="mm-error" role="alert">{{error}}</p><p v-if="loading">加载中…</p><article v-for="item in items" :key="item.id" class="mm-panel"><h2>{{item.aftersaleNo}}</h2><p>{{item.reason}}</p><p>申请商品退款 <PriceText :cents="item.goodsAmountCents" /> · 运费退款 <PriceText :cents="item.freightAmountCents" /></p><div class="mm-actions"><RouterLink :to="`/aftersales/${item.id}`">查看售后及证据</RouterLink><RouterLink :to="`/orders/${item.orderNo}`">查看订单</RouterLink><span class="mm-chip">{{AFTERSALE_STATUS_TEXT[item.status]}}</span></div><form v-if="canHandle&&['PENDING_MANUAL','SELLER_REJECTED','CLOSED'].includes(item.status)" class="mm-form" @submit.prevent="resolve(item.id)"><label>裁决<select v-model="decision[item.id]"><option value="REJECT">驳回申请</option><option v-if="isSuper" value="REFUND">按申请金额退款</option></select></label><label>依据与说明<textarea v-model="notes[item.id]" required maxlength="500" /></label><MmButton type="submit" :disabled="busy">确认处理</MmButton></form></article><p v-if="!loading&&!items.length">暂无售后申请</p><MmPagination :page="page" :total-pages="pages" @change="page=$event;load()" /></section>
</template>

<script setup lang="ts">
import {askConfirmation} from '../../shared/confirm'
import {computed,ref} from 'vue'
import {useAdminList} from './useAdminList'
import {useAuthStore} from '../../shared/stores/auth'
import {AFTERSALE_STATUS_TEXT,type AdminAftersaleItem} from '../../shared/types'
import MmButton from '../../shared/components/MmButton.vue'
import MmPagination from '../../shared/components/MmPagination.vue'
import PriceText from '../../shared/components/PriceText.vue'
const auth=useAuthStore(),status=ref('PENDING_MANUAL'),notes=ref<Record<number,string>>({}),decision=ref<Record<number,string>>({})
const isSuper=computed(()=>auth.me?.roles.includes('SUPER_ADMIN')),canHandle=computed(()=>auth.me?.roles.some(r=>['SUPER_ADMIN','SUPPORT'].includes(r)))
const {items,page,pages,loading,busy,error,load,action}=useAdminList<AdminAftersaleItem>('/admin/aftersales',()=>({status:status.value}))
async function resolve(id:number){if(!await askConfirmation('确认按所选裁决处理该售后申请？'))return;await action(`/admin/aftersales/${id}/resolve`,{action:decision.value[id]||'REJECT',note:notes.value[id]})}
</script>
