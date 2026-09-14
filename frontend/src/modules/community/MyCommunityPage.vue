<script setup lang="ts">
import {ref,watch} from 'vue'
import {get,del,patch,type ApiError} from '../../shared/api'
import {formatTime} from '../../shared/format'
import type {CommunityPage,Favorite,Follow,Footprint,Rating,Report} from './types'
import MmButton from '../../shared/components/MmButton.vue'
import MmPagination from '../../shared/components/MmPagination.vue'
import PriceText from '../../shared/components/PriceText.vue'
const tabs=[{key:'favorites',name:'收藏'},{key:'follows',name:'关注'},{key:'footprints',name:'足迹'},{key:'ratings/me',name:'我的评价'},{key:'reports/me',name:'举报进度'}]
const tab=ref('favorites'),page=ref(0),totalPages=ref(0),error=ref(''),loading=ref(false),busy=ref(false),enabled=ref(true)
const favorites=ref<Favorite[]>([]),follows=ref<Follow[]>([]),footprints=ref<Footprint[]>([]),ratings=ref<Rating[]>([]),reports=ref<Report[]>([]),count=ref(0)
let requestId=0
async function load(){
  const current=++requestId, selected=tab.value
  loading.value=true;error.value=''
  try{
    const result=await get<CommunityPage<Favorite|Follow|Footprint|Rating|Report>>('/community/'+selected,{page:page.value,size:15})
    if(current!==requestId)return
    totalPages.value=result.totalPages;count.value=result.total
    if(selected==='favorites')favorites.value=result.items as Favorite[]
    if(selected==='follows')follows.value=result.items as Follow[]
    if(selected==='footprints'){
      footprints.value=result.items as Footprint[]
      const setting=await get<{enabled:boolean}>('/community/footprints/setting')
      if(current===requestId)enabled.value=setting.enabled
    }
    if(selected==='ratings/me')ratings.value=result.items as Rating[]
    if(selected==='reports/me')reports.value=result.items as Report[]
  }catch(e){if(current===requestId)error.value=(e as ApiError).message}
  finally{if(current===requestId)loading.value=false}
}
async function remove(path:string){busy.value=true;try{await del('/community/'+path);await load()}catch(e){error.value=(e as ApiError).message}finally{busy.value=false}}
async function setting(){busy.value=true;try{await patch('/community/footprints/setting',{enabled:!enabled.value});enabled.value=!enabled.value}catch(e){error.value=(e as ApiError).message}finally{busy.value=false}}
watch(tab,()=>{page.value=0;void load()},{immediate:true})
</script>
<template><section class="mm-page"><h1>我的社区</h1><nav class="mm-actions"><MmButton v-for="t in tabs" :key="t.key" :variant="tab===t.key?'primary':'ghost'" @click="tab=t.key">{{t.name}}</MmButton><RouterLink to="/community/demands?mine=1">我的求购</RouterLink></nav><p v-if="error" class="mm-error" role="alert">{{error}}</p><p v-if="loading">加载中…</p><div v-if="tab==='footprints'" class="mm-panel"><p>仅保留最近 30 天足迹，可随时关闭记录或清空。</p><div class="mm-actions"><MmButton :disabled="busy" @click="setting">{{enabled?'关闭足迹记录':'开启足迹记录'}}</MmButton><MmButton variant="ghost" :disabled="busy" @click="remove('footprints')">清空我的足迹</MmButton></div></div><template v-if="!loading"><template v-if="tab==='favorites'"><article v-for="f in favorites" :key="f.id" class="mm-panel"><RouterLink v-if="f.isPublicVisible" :to="`/products/${f.productId}`">{{f.productTitle}}</RouterLink><strong v-else>{{f.productTitle||'商品已不可见'}}</strong><PriceText :cents="f.productPriceCents" /><p class="mm-muted">{{f.isPublicVisible?'在售':'商品已下架或不可见'}} · {{formatTime(f.collectedAt)}}</p><MmButton variant="ghost" :disabled="busy" @click="remove(`favorites/${f.productId}`)">取消收藏</MmButton></article></template><template v-if="tab==='follows'"><article v-for="f in follows" :key="f.id" class="mm-panel"><RouterLink :to="`/sellers/${f.sellerId}`">{{f.sellerNickname}}</RouterLink><p class="mm-muted">{{formatTime(f.followedAt)}}</p><MmButton variant="ghost" :disabled="busy" @click="remove(`follows/${f.sellerId}`)">取消关注</MmButton></article></template><template v-if="tab==='footprints'"><article v-for="f in footprints" :key="f.id" class="mm-panel"><RouterLink v-if="f.productStatus==='ON_SALE'" :to="`/products/${f.productId}`">{{f.productTitle}}</RouterLink><strong v-else>{{f.productTitle||'商品不可见'}}</strong><p><PriceText :cents="f.productPriceCents" /> · {{formatTime(f.viewedAt)}}</p><MmButton variant="ghost" :disabled="busy" @click="remove(`footprints/${f.productId}`)">移除此足迹</MmButton></article></template><template v-if="tab==='ratings/me'"><article v-for="r in ratings" :key="r.id" class="mm-panel"><strong>{{r.rating}} / 5 分</strong><p>{{r.comment||'无文字评价'}}</p><p class="mm-muted">{{formatTime(r.createdAt)}} · 退款状态 {{r.refundStatus}}</p></article></template><template v-if="tab==='reports/me'"><article v-for="r in reports" :key="r.id" class="mm-panel"><strong>举报 #{{r.id}} · {{r.status==='PENDING'?'处理中':r.status==='RESOLVED'?'已处理':r.status}}</strong><p>{{r.reason}}</p><p>{{r.processNote||'等待平台处理'}}</p><p class="mm-muted">{{formatTime(r.createdAt)}}</p></article></template><p v-if="count===0" class="mm-panel mm-muted">这里还没有记录。</p></template><MmPagination :page="page" :total-pages="totalPages" @change="page=$event;load()" /></section></template>
