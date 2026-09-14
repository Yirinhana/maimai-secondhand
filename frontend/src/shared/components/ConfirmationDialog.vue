<script setup lang="ts">
import {nextTick,ref,watch,onBeforeUnmount} from 'vue'
import {confirmation,answerConfirmation} from '../confirm'
import MmButton from './MmButton.vue'
const dialog=ref<HTMLDialogElement|null>(null)
let previousFocus:HTMLElement|null=null
watch(confirmation,async value=>{
  await nextTick()
  if(value){previousFocus=document.activeElement as HTMLElement|null;dialog.value?.showModal()}
  else {dialog.value?.close();previousFocus?.focus()}
})
onBeforeUnmount(()=>answerConfirmation(false))
</script>
<template><dialog ref="dialog" class="mm-confirm-dialog" aria-labelledby="confirmation-title" @cancel.prevent="answerConfirmation(false)"><h2 id="confirmation-title">请确认操作</h2><p>{{confirmation?.message}}</p><div class="mm-actions"><MmButton variant="ghost" autofocus @click="answerConfirmation(false)">暂不操作</MmButton><MmButton @click="answerConfirmation(true)">确认操作</MmButton></div></dialog></template>
<style scoped>.mm-confirm-dialog{width:min(440px,calc(100vw - 32px));padding:24px;border:1px solid var(--mm-border);border-radius:16px;color:var(--mm-ink);box-shadow:0 12px 80px #241d3430}.mm-confirm-dialog::backdrop{background:#241d346b}.mm-confirm-dialog p{margin:16px 0;white-space:pre-wrap}.mm-confirm-dialog .mm-actions{justify-content:flex-end}</style>
