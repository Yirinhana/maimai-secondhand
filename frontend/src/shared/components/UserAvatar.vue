<template>
  <span class="mm-avatar" :style="{ width: `${size}px`, height: `${size}px` }">
    <img
      v-if="src && !failed"
      :src="src"
      :alt="`${nickname}的头像`"
      @error="failed = true"
    />
    <span v-else aria-hidden="true">{{
      nickname.trim().slice(0, 1) || '麦'
    }}</span>
  </span>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
const props = withDefaults(
  defineProps<{ src?: string | null; nickname: string; size?: number }>(),
  { size: 38 },
);
const failed = ref(false);
watch(
  () => props.src,
  () => {
    failed.value = false;
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
