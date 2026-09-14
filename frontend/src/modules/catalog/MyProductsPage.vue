<template>
  <section class="mm-page"><div class="mm-actions"><h1>我的商品</h1><RouterLink to="/publish">发布新闲置</RouterLink></div><nav class="mm-actions"><RouterLink to="/seller/orders">卖家订单</RouterLink><RouterLink to="/seller/bargains">收到的议价</RouterLink></nav><label>状态 <select v-model="status" @change="page=0;load()"><option value="">全部</option><option v-for="(label,key) in PRODUCT_STATUS_TEXT" :key="key" :value="key">{{label}}</option></select></label><p v-if="error" class="mm-error" role="alert">{{error}}</p><p v-if="loading">加载中…</p><article v-for="p in items" :key="p.id" class="mm-panel"><div class="mm-item"><ItemImage v-if="p.coverImage" :src="p.coverImage" :alt="p.title" /><div class="mm-item-body"><h2>{{p.title}}</h2><PriceText :cents="p.priceCents" /><p class="mm-muted">可售 {{p.stockAvailable}} · 预留 {{p.stockReserved}} · 已售 {{p.stockSold}}</p></div><span class="mm-chip">{{PRODUCT_STATUS_TEXT[p.status]}}</span></div><p v-if="p.reviewReason">审核说明：{{p.reviewReason}}</p><div class="mm-actions"><RouterLink :to="`/publish/${p.id}`">编辑 / 图片 / 库存</RouterLink><RouterLink v-if="p.status==='ON_SALE'" :to="`/products/${p.id}`">查看商品</RouterLink><MmButton v-if="['DRAFT','REJECTED','OFF_SHELF'].includes(p.status)" :disabled="busy" @click="act(p.id,'submit')">提交审核</MmButton><MmButton v-if="p.status==='ON_SALE'" variant="ghost" :disabled="busy" @click="act(p.id,'off-shelf')">下架</MmButton></div></article><EmptyState v-if="!loading&&!items.length" title="暂无商品" description="先申请卖家，审核后即可发布闲置" /><MmPagination :page="page" :total-pages="totalPages" @change="page=$event;load()" /></section>
</template>

<script setup lang="ts">
import ItemImage from '../../shared/components/ItemImage.vue'
import {onMounted,ref} from 'vue'
import {get,post,type ApiError} from '../../shared/api'
import {PRODUCT_STATUS_TEXT,type Page,type SellerProductItem} from '../../shared/types'
import MmButton from '../../shared/components/MmButton.vue'
import MmPagination from '../../shared/components/MmPagination.vue'
import EmptyState from '../../shared/components/EmptyState.vue'
import PriceText from '../../shared/components/PriceText.vue'
const items=ref<SellerProductItem[]>([]),page=ref(0),totalPages=ref(0),status=ref(''),error=ref(''),loading=ref(true),busy=ref(false)
async function load(){loading.value=true;error.value='';try{const r=await get<Page<SellerProductItem>>('/seller/products',{status:status.value,page:page.value,size:12});items.value=r.content;totalPages.value=r.totalPages}catch(e){error.value=(e as ApiError).message}finally{loading.value=false}}
async function act(id:number,action:string){busy.value=true;try{await post(`/seller/products/${id}/${action}`);await load()}catch(e){error.value=(e as ApiError).message}finally{busy.value=false}}
onMounted(load)
</script>
