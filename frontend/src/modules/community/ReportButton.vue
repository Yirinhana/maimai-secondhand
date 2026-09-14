<script setup lang="ts">
import {ref} from 'vue'
import {post,type ApiError} from '../../shared/api'
import MmButton from '../../shared/components/MmButton.vue'
const props=defineProps<{resourceType:'PRODUCT'|'DEMAND_POST'|'DEMAND_REPLY'|'ORDER_REVIEW';resourceId:number}>()
const open=ref(false),reason=ref(''),busy=ref(false),feedback=ref('')
async function submit(){if(busy.value)return;busy.value=true;feedback.value='';try{await post('/community/reports',{resourceType:props.resourceType,resourceId:props.resourceId,reason:reason.value.trim()});feedback.value='举报已提交，可在个人社区查看处理进度';open.value=false;reason.value=''}catch(e){feedback.value=(e as ApiError).message}finally{busy.value=false}}
</script>
<template><div><MmButton variant="ghost" @click="open=!open">举报</MmButton><form v-if="open" class="mm-form" @submit.prevent="submit"><label>举报原因<textarea v-model="reason" minlength="6" maxlength="500" required placeholder="请说明具体问题（6—500字）" /></label><div class="mm-actions"><MmButton type="submit" :loading="busy">提交举报</MmButton><MmButton variant="ghost" @click="open=false">取消</MmButton></div></form><p v-if="feedback" class="mm-muted" role="status">{{feedback}}</p></div></template>
