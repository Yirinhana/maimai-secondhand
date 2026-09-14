<template>
  <section class="product-gallery" aria-label="商品图片">
    <button
      class="product-gallery__main"
      type="button"
      aria-label="放大查看商品图片"
      :disabled="!currentImage"
      @click="viewerOpen = true"
    >
      <ItemImage
        v-if="currentImage"
        :src="currentImage.path"
        :alt="`${title} 图片 ${currentIndex + 1}`"
        loading="eager"
      />
      <span v-else>暂无图片</span>
      <span v-if="currentImage" class="product-gallery__zoom" aria-hidden="true"
        ><svg
          viewBox="0 0 24 24"
          width="16"
          height="16"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
        >
          <circle cx="10" cy="10" r="6" />
          <path d="m15 15 5 5M7 10h6m-3-3v6" /></svg
        >查看大图</span
      >
    </button>
    <div class="product-gallery__caption">
      <span>{{
        isDemoProductImage(currentImage?.path)
          ? '演示示意图 · 非卖家实拍'
          : images.length
            ? '商品图片'
            : '卖家暂未上传图片'
      }}</span
      ><span v-if="images.length"
        >{{ currentIndex + 1 }} / {{ images.length }}</span
      >
    </div>
    <ul v-if="images.length > 1" class="product-gallery__thumbs">
      <li v-for="(item, index) in images" :key="item.id">
        <button
          class="product-gallery__thumb"
          :class="{ 'is-active': index === currentIndex }"
          type="button"
          :aria-label="`查看第 ${index + 1} 张图片`"
          :aria-pressed="index === currentIndex"
          @click="currentIndex = index"
        >
          <ItemImage :src="item.path" :alt="`${title} 缩略图 ${index + 1}`" />
        </button>
      </li>
    </ul>
    <CatalogDialog
      v-model:open="viewerOpen"
      id="product-image-viewer"
      title="商品大图"
      wide
      @keydown.left.prevent="move(-1)"
      @keydown.right.prevent="move(1)"
    >
      <div class="product-gallery__viewer">
        <ItemImage
          v-if="currentImage"
          :src="currentImage.path"
          :alt="`${title} 大图 ${currentIndex + 1}`"
          loading="eager"
        />
        <div class="product-gallery__viewer-controls">
          <button
            v-if="images.length > 1"
            type="button"
            aria-label="上一张图片"
            @click="move(-1)"
          >
            ← 上一张
          </button>
          <p role="status">
            第 {{ currentIndex + 1 }} 张，共 {{ images.length }} 张
          </p>
          <button
            v-if="images.length > 1"
            type="button"
            aria-label="下一张图片"
            @click="move(1)"
          >
            下一张 →
          </button>
        </div>
        <p class="product-gallery__tip">
          {{ images.length > 1 ? '可用左右方向键切换，' : '' }}按 Esc 关闭大图。
        </p>
      </div>
    </CatalogDialog>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { isDemoProductImage } from '../../../shared/demoImages';
import ItemImage from '../../../shared/components/ItemImage.vue';
import type { ProductImage } from '../../../shared/types';
import CatalogDialog from './CatalogDialog.vue';
const props = defineProps<{ images: ProductImage[]; title: string }>();
const currentIndex = ref(0);
const viewerOpen = ref(false);
const currentImage = computed(() => props.images[currentIndex.value] ?? null);
function move(direction: number) {
  if (props.images.length)
    currentIndex.value =
      (currentIndex.value + direction + props.images.length) %
      props.images.length;
}
watch(
  () => props.images,
  () => {
    currentIndex.value = 0;
    viewerOpen.value = false;
  },
);
</script>

<style scoped>
.product-gallery {
  min-width: 0;
}
.product-gallery__main {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  aspect-ratio: 1;
  padding: 0;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  overflow: hidden;
  background: #f0eee8;
  color: var(--mm-muted);
  cursor: zoom-in;
}
.product-gallery__main:disabled {
  cursor: default;
}
.product-gallery__main > img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.product-gallery__zoom {
  position: absolute;
  bottom: 16px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 11px;
  border: 1px solid #e1dcd1;
  border-radius: 24px;
  background: #fffdf8ed;
  color: var(--mm-ink);
  font-size: 12px;
}
.product-gallery__caption {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  color: var(--mm-muted);
  font-size: 12px;
}
.product-gallery__thumbs {
  display: flex;
  gap: 10px;
  max-width: 100%;
  overflow-x: auto;
  padding: 4px;
  margin: 10px -4px 0;
}
.product-gallery__thumbs > li {
  flex-shrink: 0;
}
.product-gallery__thumb {
  display: block;
  width: 68px;
  height: 68px;
  padding: 3px;
  border: 1px solid var(--mm-border);
  border-radius: 7px;
  background: var(--mm-white);
}
.product-gallery__thumb.is-active {
  border: 2px solid var(--mm-ink);
  padding: 2px;
}
.product-gallery__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 3px;
}
.product-gallery button:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: 3px;
}
.product-gallery__viewer > img {
  display: block;
  width: 100%;
  height: min(65dvh, 720px);
  object-fit: contain;
  background: #f3f1ec;
  border-radius: 6px;
}
.product-gallery__viewer-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 16px;
}
.product-gallery__viewer-controls button {
  min-height: 42px;
  border: 1px solid var(--mm-border);
  border-radius: 6px;
  padding: 8px 12px;
  background: var(--mm-white);
  font-size: 13px;
}
.product-gallery__viewer-controls p,
.product-gallery__tip {
  color: var(--mm-muted);
  font-size: 12px;
  text-align: center;
}
.product-gallery__tip {
  margin-top: 12px;
}
@media (min-width: 1000px) {
  .product-gallery {
    position: sticky;
    top: 24px;
    align-self: start;
  }
}
@media (max-width: 600px) {
  .product-gallery__zoom {
    right: 12px;
    bottom: 12px;
  }
  .product-gallery__viewer-controls {
    gap: 10px;
  }
  .product-gallery__viewer > img {
    height: 52dvh;
  }
}
</style>
