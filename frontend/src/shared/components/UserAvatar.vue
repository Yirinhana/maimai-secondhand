<template>
  <span class="mm-avatar" :style="{ width: `${size}px`, height: `${size}px` }">
    <img
      v-if="imageSrc"
      :src="imageSrc"
      :alt="`${nickname}的头像`"
      decoding="async"
      @error="onError"
    />
    <span v-else aria-hidden="true">{{
      nickname.trim().slice(0, 1) || '麦'
    }}</span>
  </span>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { defaultAvatar } from '../defaultAvatars';
const props = withDefaults(
  defineProps<{ src?: string | null; nickname: string; size?: number }>(),
  { size: 38 },
);
const failed = ref(false);
const fallbackFailed = ref(false);
const imageSrc = computed(() =>
  props.src && !failed.value
    ? props.src
    : fallbackFailed.value
      ? null
      : defaultAvatar(props.nickname),
);
function onError() {
  if (props.src && !failed.value) failed.value = true;
  else fallbackFailed.value = true;
}
watch(
  () => [props.src, props.nickname],
  () => {
    failed.value = false;
    fallbackFailed.value = false;
  },
);
</script>
<style scoped>
.mm-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  border-radius: 50%;
  background: #efe6d8;
  color: #3d342c;
  border: 1px solid #dfd4c3;
  overflow: hidden;
  font-weight: 750;
  font-size: 16px;
  vertical-align: middle;
}
.mm-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
