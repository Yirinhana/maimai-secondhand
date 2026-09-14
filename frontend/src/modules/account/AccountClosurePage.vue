<script setup lang="ts">
import {ref} from 'vue'
import {post,type ApiError} from '../../shared/api'
import {askConfirmation} from '../../shared/confirm'
import {useAuthStore} from '../../shared/stores/auth'
import MmButton from '../../shared/components/MmButton.vue'
const auth=useAuthStore(),password=ref(''),reason=ref(''),accepted=ref(false),busy=ref(false),error=ref(''),notice=ref('')
async function submit(){
  if(busy.value||!accepted.value)return
  if(!await askConfirmation('确认申请注销并停用当前账号？成功后将退出登录，不能继续使用该账号交易。'))return
  busy.value=true;error.value=''
  try{
    const result=await post<{id:number;status:string;notice:string}>('/me/closure',{password:password.value,reason:reason.value.trim()})
    password.value='';notice.value=result.notice
    await auth.fetchMe()
  }catch(e){error.value=(e as ApiError).message||'注销申请提交失败'}
  finally{busy.value=false}
}
</script>
<template>
  <section class="mm-page">
    <h1>账号注销申请</h1>
    <div v-if="notice" class="mm-panel"><p role="status">{{notice}}</p><RouterLink to="/">返回首页</RouterLink></div>
    <form v-else class="mm-panel mm-form" @submit.prevent="submit">
      <p>提交前请完成未结束的交易、售后和退款。收货后常规售后期尚未结束时，也需要先等待处理窗口结束。</p>
      <p class="mm-notice">申请通过后账号将停用并退出登录。交易、争议和必要审计记录按规则保留；此操作不会立即删除所有历史记录。</p>
      <label>当前密码<input v-model="password" type="password" autocomplete="current-password" required /></label>
      <label>申请原因<textarea v-model="reason" required maxlength="500" /></label>
      <label class="closure-check"><input v-model="accepted" type="checkbox" />我已了解停用影响并确认申请</label>
      <p v-if="error" class="mm-error" role="alert">{{error}}</p>
      <div class="mm-actions"><MmButton type="submit" variant="danger" :loading="busy" :disabled="!accepted">申请注销</MmButton><RouterLink to="/me">返回个人中心</RouterLink></div>
    </form>
  </section>
</template>
<style scoped>.closure-check{display:flex;flex-direction:row;align-items:center;gap:10px}.closure-check input{width:auto}</style>
