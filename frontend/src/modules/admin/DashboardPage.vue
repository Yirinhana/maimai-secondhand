<template>
  <section class="mm-stack"><h1>平台总览</h1><p class="mm-muted">统计来自当前数据库。隔离环境中的支付数据仅用于联调。</p><p v-if="error" class="mm-error" role="alert">{{error}}</p><div v-if="stats" class="mm-grid"><div v-for="m in metrics" :key="m.key" class="mm-panel"><span class="mm-muted">{{m.label}}</span><strong style="font-size:30px">{{stats[m.key]}}</strong></div><div class="mm-panel"><span class="mm-muted">平台服务费累计</span><PriceText :cents="stats.platformFeeSumCents" /></div></div><div class="mm-panel"><h2>处理队列</h2><nav class="mm-actions"><RouterLink to="/admin/seller-apps">卖家准入</RouterLink><RouterLink to="/admin/products">商品审核</RouterLink><RouterLink to="/admin/community">社区与举报</RouterLink><RouterLink to="/admin/aftersales">售后仲裁</RouterLink></nav></div></section>
</template>

<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {get,type ApiError} from '../../shared/api'
import type {AdminStatsOverview} from '../../shared/types'
import PriceText from '../../shared/components/PriceText.vue'
const stats=ref<AdminStatsOverview|null>(null),error=ref('')
const metrics:{key:keyof AdminStatsOverview;label:string}[]=[{key:'userCount',label:'注册用户'},{key:'productOnSaleCount',label:'在售商品'},{key:'orderCount',label:'全部订单'},{key:'paidOrderCount',label:'已支付订单'},{key:'refundSuccessCount',label:'成功退款记录'}]
onMounted(async()=>{try{stats.value=await get<AdminStatsOverview>('/admin/stats/overview')}catch(e){error.value=(e as ApiError).message}})
</script>
