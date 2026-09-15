<script setup lang="ts">
import CatalogDialog from '../catalog/components/CatalogDialog.vue';
const preview = ref<string | null>(null);
const previewOpen = ref(false);
import { onMounted, ref } from 'vue';
import { get, upload, type ApiError } from '../../shared/api';
import { formatTime } from '../../shared/format';
import MmButton from '../../shared/components/MmButton.vue';
interface EvidenceImage {
  id: string;
  url: string;
  uploadedBy: number;
  createdAt: string;
}
const props = defineProps<{ aftersaleId: number; canUpload: boolean }>(),
  items = ref<EvidenceImage[]>([]),
  busy = ref(false),
  error = ref(''),
  fileInput = ref<HTMLInputElement | null>(null);
async function load() {
  try {
    items.value = await get<EvidenceImage[]>(
      `/aftersales/${props.aftersaleId}/images`,
    );
  } catch (e) {
    error.value = (e as ApiError).message || '证据图片加载失败';
  }
}
async function selected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (
    !['image/jpeg', 'image/png'].includes(file.type) ||
    file.size > 5 * 1024 * 1024
  ) {
    error.value = '请选择不超过5MB的JPG或PNG图片';
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    const form = new FormData();
    form.append('file', file);
    await upload(`/aftersales/${props.aftersaleId}/images`, form);
    await load();
  } catch (e) {
    error.value = (e as ApiError).message;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="mm-panel">
    <h2>售后证据图片</h2>
    <p class="mm-muted">
      仅交易双方与有权限的客服可查看。最多 12 张，每张不超过
      5MB，上传前遮挡无关的个人信息。
    </p>
    <p v-if="error" class="mm-error" role="alert">{{ error }}</p>
    <div class="mm-grid">
      <figure v-for="(image, index) in items" :key="image.id">
        <button
          type="button"
          class="evidence-preview"
          @click="
            preview = image.url;
            previewOpen = true;
          "
        >
          <img
            :src="image.url"
            :alt="`售后证据 ${index + 1}`"
            loading="lazy"
            style="max-height: 240px; object-fit: contain"
          />
        </button>
        <figcaption class="mm-muted">
          {{ formatTime(image.createdAt) }}
        </figcaption>
      </figure>
    </div>
    <template v-if="canUpload"
      ><input
        ref="fileInput"
        type="file"
        accept="image/jpeg,image/png"
        hidden
        @change="selected"
      /><MmButton
        variant="ghost"
        :loading="busy"
        :disabled="items.length >= 12"
        @click="fileInput?.click()"
        >补充证据图片</MmButton
      ></template
    >
    <p v-if="!items.length && !error" class="mm-muted">尚未上传证据图片</p>
    <CatalogDialog
      v-model:open="previewOpen"
      id="evidence-preview"
      title="售后证据图片"
      wide
      ><img
        v-if="preview"
        :src="preview"
        alt="售后证据大图"
        style="width: 100%; max-height: 75vh; object-fit: contain"
    /></CatalogDialog>
  </section>
</template>

<style scoped>
.evidence-preview {
  padding: 0;
  border: 1px solid var(--mm-border);
  border-radius: 8px;
  background: var(--mm-canvas);
  width: 100%;
  cursor: zoom-in;
}
.evidence-preview img {
  width: 100%;
}
</style>
