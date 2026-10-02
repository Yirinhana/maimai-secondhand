<script setup lang="ts">
import { ref } from 'vue';
import { upload } from '../../shared/api';
import { useAuthStore } from '../../shared/stores/auth';
const ownerId = useAuthStore().me!.id;
const props = defineProps<{ modelValue: string[] }>(),
  emit = defineEmits<{ 'update:modelValue': [string[]]; busy: [boolean] }>();
const error = ref(''),
  busy = ref(false);
async function select(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = '';
  if (files.length + props.modelValue.length > 9) {
    error.value = '草稿最多保留9张图片';
    return;
  }
  busy.value = true;
  emit('busy', true);
  error.value = '';
  let ids = [...props.modelValue];
  try {
    for (const file of files) {
      const form = new FormData();
      form.append('purpose', 'DRAFT');
      form.append('ownerId', String(ownerId));
      form.append('file', file);
      const image = await upload<{ id: string }>('/me/media', form);
      ids = [...ids, image.id];
      emit('update:modelValue', ids);
    }
  } catch (e) {
    error.value = (e as Error).message;
  } finally {
    busy.value = false;
    emit('busy', false);
  }
}
</script>
<template>
  <section class="draft-photos">
    <h3>草稿照片</h3>
    <p class="mm-muted">
      JPG /
      PNG，每张不超过5MB。上传后仅本人可见，保存成商品时会一并转入商品相册。
    </p>
    <label
      >添加照片<input
        type="file"
        multiple
        accept="image/jpeg,image/png"
        :disabled="busy || modelValue.length >= 9"
        @change="select"
    /></label>
    <p v-if="busy" role="status">正在上传照片，请暂时不要离开页面…</p>
    <div class="draft-photo-grid">
      <figure v-for="(id, i) in modelValue" :key="id">
        <img
          :src="`/api/v1/me/media/${id}`"
          :alt="`未发布草稿照片${i + 1}`"
          width="100"
          height="100"
        /><button
          type="button"
          :disabled="busy"
          @click="
            emit(
              'update:modelValue',
              modelValue.filter((v) => v !== id),
            )
          "
        >
          移除第{{ i + 1 }}张
        </button>
      </figure>
    </div>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
  </section>
</template>
<style scoped>
.draft-photos {
  margin: 18px 0;
}
.draft-photos input {
  display: block;
  max-width: 100%;
  margin-top: 8px;
}
.draft-photo-grid {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.draft-photo-grid figure {
  margin: 12px 0;
  display: grid;
  gap: 6px;
}
.draft-photo-grid img {
  object-fit: cover;
  border-radius: 8px;
}
.draft-photo-grid button {
  min-height: 36px;
}
</style>
