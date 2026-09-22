<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {get,type ApiError} from '../../shared/api'
import {formatTime} from '../../shared/format'
import MmButton from '../../shared/components/MmButton.vue'
interface Todo{id:number;orderId:number;orderNo:string;buyerId:number;sellerId:number;reason:string;createdAt:string}
const items=ref<Todo[]>([]),page=ref(0),loading=ref(false),error=ref('')
async function load(){loading.value=true;error.value='';try{items.value=await get<Todo[]>('/admin/trade-todos',{page:page.value,size:20})}catch(e){error.value=(e as ApiError).message}finally{loading.value=false}}
onMounted(load)
</script>
<template><section><div class="mm-actions"><h1>交易待办</h1><MmButton variant="ghost" :disabled="loading" @click="load">刷新</MmButton></div><p class="mm-muted">发货逾期、到期仍未核实签收及面交逾约的订单进入人工关注。按待办原因核对交付情况，不自动视为买家已收到货物。</p><p v-if="error" class="mm-error" role="alert">{{error}}</p><p v-if="loading">加载中…</p><template v-else><article v-for="item in items" :key="item.id" class="mm-panel"><strong>{{item.orderNo}}</strong><p>{{item.reason}}</p><p class="mm-muted">买家 #{{item.buyerId}} · 卖家 #{{item.sellerId}} · {{formatTime(item.createdAt)}}</p><RouterLink :to="`/admin/orders/${item.orderNo}`">核对订单、交付与关联工单 →</RouterLink></article><p v-if="!items.length" class="mm-panel mm-muted">当前没有待跟进记录。</p></template><nav class="mm-actions" aria-label="待办分页"><MmButton variant="ghost" :disabled="loading||page===0" @click="page--;load()">上一页</MmButton><span>第 {{page+1}} 页</span><MmButton variant="ghost" :disabled="loading||items.length<20" @click="page++;load()">下一页</MmButton></nav></section></template>
