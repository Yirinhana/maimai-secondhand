<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { productImageUrl, type ProductImageSize } from '../productImages';
const props = withDefaults(
  defineProps<{
    src?: string | null;
    alt: string;
    loading?: 'lazy' | 'eager';
    size?: ProductImageSize;
  }>(),
  { loading: 'lazy', size: 'card' },
);
const failed = ref(false),
  originalFallback = ref(false);
const unavailable = computed(() => !props.src || failed.value);
const displaySrc = computed(() =>
  productImageUrl(props.src, originalFallback.value ? 'original' : props.size),
);
function onError() {
  if (!originalFallback.value && displaySrc.value !== props.src)
    originalFallback.value = true;
  else failed.value = true;
}
watch(
  () => [props.src, props.size],
  () => {
    failed.value = false;
    originalFallback.value = false;
  },
);
</script>
<template>
  <img
    :src="unavailable ? '/brand/item-placeholder.svg' : displaySrc"
    :alt="unavailable ? `${alt}（图片暂不可用）` : alt"
    :loading="loading"
    decoding="async"
    @error="onError"
  />
</template>
