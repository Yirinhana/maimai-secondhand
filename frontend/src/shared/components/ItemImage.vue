<script setup lang="ts">
import {computed,ref,watch} from 'vue'
const props=withDefaults(defineProps<{src?:string|null;alt:string;loading?:'lazy'|'eager'}>(),{loading:'lazy'})
const failed=ref(false)
const unavailable=computed(()=>!props.src||failed.value)
watch(()=>props.src,()=>{failed.value=false})
</script>
<template>
  <img :src="unavailable?'/brand/item-placeholder.svg':src!" :alt="unavailable?`${alt}（图片暂不可用）`:alt" :loading="loading" decoding="async" @error="failed=true" />
</template>
