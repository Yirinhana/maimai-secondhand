<script setup lang="ts">
import {ref} from 'vue'
import {post,type ApiError} from '../../shared/api'
import {askConfirmation} from '../../shared/confirm'
import {useAuthStore} from '../../shared/stores/auth'
import MmButton from '../../shared/components/MmButton.vue'
const props=defineProps<{userId:number;nickname:string}>(),emit=defineEmits<{updated:[]}>(),auth=useAuthStore()
const expanded=ref(false),role=ref('OPERATOR'),grant=ref(true),reason=ref(''),busy=ref(false),error=ref('')
const names:Record<string,string>={OPERATOR:'运营',SUPPORT:'客服',SUPER_ADMIN:'超级管理员'}
async function save(){
  if(busy.value)return
  if(!await askConfirmation(`确认${grant.value?'授予':'撤销'}「${props.nickname}」的${names[role.value]}角色？`))return
  busy.value=true;error.value=''
  try{await post(`/admin/users/${props.userId}/roles`,{role:role.value,grant:grant.value,reason:reason.value.trim()});reason.value='';expanded.value=false;emit('updated');if(props.userId===auth.me?.id)await auth.fetchMe()}
  catch(e){error.value=(e as ApiError).message}
  finally{busy.value=false}
}
</script>
<template><div><MmButton variant="ghost" @click="expanded=!expanded">管理授权</MmButton><form v-if="expanded" class="mm-form" @submit.prevent="save"><label>管理角色<select v-model="role"><option v-for="(label,key) in names" :key="key" :value="key">{{label}}</option></select></label><label>操作<select v-model="grant"><option :value="true">授予角色</option><option :value="false">撤销角色</option></select></label><label>授权变更原因<textarea v-model="reason" required maxlength="500" /></label><p class="mm-muted">管理授权会改变后台访问能力，平台会保留操作记录；最后一名超级管理员不能被撤销。</p><p v-if="error" class="mm-error" role="alert">{{error}}</p><MmButton type="submit" :loading="busy">保存授权变更</MmButton></form></div></template>
