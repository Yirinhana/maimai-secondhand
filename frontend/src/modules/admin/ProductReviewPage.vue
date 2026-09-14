<template>
  <section class="mm-stack"><h1>商品审核</h1><label>状态 <select v-model="status" @change="page=0;load()"><option value="PENDING_REVIEW">首次审核</option><option value="CHANGES_REVIEW">修改复审</option><option value="ON_SALE">在售</option><option value="REJECTED">已驳回</option></select></label><p v-if="error" class="mm-error" role="alert">{{error}}</p><p v-if="loading">加载中…</p><article v-for="item in items" :key="item.id" class="mm-panel"><div class="mm-item"><ItemImage v-if="item.coverImage" :src="item.coverImage" :alt="item.title" /><div class="mm-item-body"><h2>{{item.title}}</h2><p>{{item.sellerNickname}} · {{item.region}} · <PriceText :cents="item.priceCents" /></p><p>{{PRODUCT_STATUS_TEXT[item.status]}}</p></div></div><MmButton variant="ghost" :disabled="busy" @click="inspect(item.id)">查看完整审核材料</MmButton><template v-if="selected?.id===item.id"><div class="mm-actions"><ItemImage v-for="image in selected.images" :key="image.id" :src="image.path" alt="商品审核图片" style="width:120px;height:120px;object-fit:contain" /></div><p style="white-space:pre-wrap">{{selected.description}}</p><p><strong>缺陷：</strong>{{selected.defects||'未填写'}}</p><p>运费 <PriceText :cents="selected.freightCents" /> · 退货承诺 {{selected.returnPromise||'未填写'}}</p><form v-if="canReview" class="mm-form" @submit.prevent="review(item.id,true)"><label>审核理由<textarea v-model="reason" required maxlength="500" /></label><div class="mm-actions"><MmButton type="submit" :disabled="busy">通过</MmButton><MmButton variant="danger" :disabled="busy||!reason.trim()" @click="review(item.id,false)">驳回</MmButton></div></form><ProductRevisions :key="item.id" :product-id="item.id" admin /></template></article><p v-if="!loading&&!items.length" class="mm-muted">暂无此状态商品</p><MmPagination :page="page" :total-pages="pages" @change="page=$event;load()" /></section>
</template>

<script setup lang="ts">
import ProductRevisions from '../catalog/ProductRevisions.vue'
import ItemImage from '../../shared/components/ItemImage.vue'
import {computed,onMounted,ref} from 'vue'
import {get,post,type ApiError} from '../../shared/api'
import {useAuthStore} from '../../shared/stores/auth'
import {PRODUCT_STATUS_TEXT,type AdminProductItem,type Page,type ProductDetail} from '../../shared/types'
import MmButton from '../../shared/components/MmButton.vue'
import MmPagination from '../../shared/components/MmPagination.vue'
import PriceText from '../../shared/components/PriceText.vue'
const auth=useAuthStore(),items=ref<AdminProductItem[]>([]),status=ref('PENDING_REVIEW'),page=ref(0),pages=ref(0),loading=ref(false),busy=ref(false),error=ref(''),selected=ref<ProductDetail|null>(null),reason=ref('')
const canReview=computed(()=>auth.me?.roles.some(r=>['SUPER_ADMIN','OPERATOR'].includes(r)))
async function load(){loading.value=true;error.value='';try{const r=await get<Page<AdminProductItem>>('/admin/products',{status:status.value,page:page.value,size:12});items.value=r.content;pages.value=r.totalPages}catch(e){error.value=(e as ApiError).message}finally{loading.value=false}}
async function inspect(id:number){busy.value=true;error.value='';try{selected.value=await get<ProductDetail>(`/admin/products/${id}`);reason.value=''}catch(e){error.value=(e as ApiError).message}finally{busy.value=false}}
async function review(id:number,approve:boolean){busy.value=true;try{await post(`/admin/products/${id}/review`,{approve,reason:reason.value});selected.value=null;await load()}catch(e){error.value=(e as ApiError).message}finally{busy.value=false}}
onMounted(load)
</script>
