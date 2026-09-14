<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRouter} from 'vue-router'
import {post,type ApiError} from '../../shared/api'
import {useAuthStore} from '../../shared/stores/auth'
import MmButton from '../../shared/components/MmButton.vue'
import ReportButton from './ReportButton.vue'
const props=defineProps<{productId:number;sellerId:number}>(),auth=useAuthStore(),router=useRouter(),busy=ref(false),feedback=ref('')
async function act(type:'favorite'|'message'){busy.value=true;feedback.value='';try{if(type==='favorite'){await post(`/community/favorites/${props.productId}`);feedback.value='已收藏，可在我的社区管理'}else{const r=await post<{id:number}>('/messages/conversations',{recipientId:props.sellerId,productId:props.productId});await router.push(`/messages/${r.id}`)}}catch(e){feedback.value=(e as ApiError).message}finally{busy.value=false}}
onMounted(async()=>{if(!auth.meLoaded)await auth.fetchMe();if(auth.me)post(`/community/footprints/${props.productId}`).catch(()=>{})})
</script>
<template><div class="mm-stack"><div class="mm-actions"><MmButton variant="ghost" :disabled="busy" @click="act('favorite')">收藏</MmButton><MmButton v-if="auth.me?.id!==sellerId" variant="ghost" :disabled="busy" @click="act('message')">联系卖家</MmButton><ReportButton resource-type="PRODUCT" :resource-id="productId" /></div><p v-if="feedback" class="mm-muted" role="status">{{feedback}}</p></div></template>
