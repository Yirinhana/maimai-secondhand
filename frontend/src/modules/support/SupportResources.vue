<script setup lang="ts">
import { computed } from "vue";
import ItemImage from "../../shared/components/ItemImage.vue";
import PriceText from "../../shared/components/PriceText.vue";
import { safeSupportPath, type SupportResource } from "./navigationResources";
const props = defineProps<{ resources: SupportResource[] }>();
defineEmits<{ navigate: [] }>();
const links = computed(() =>
  props.resources.filter((item) => safeSupportPath(item.path)),
);
</script>
<template>
  <ul v-if="links.length" class="support-resources" aria-label="相关商品与页面">
    <li v-for="item in links" :key="item.path">
      <RouterLink :to="item.path" @click="$emit('navigate')">
        <ItemImage
          v-if="item.kind === 'PRODUCT'"
          :src="item.imagePath"
          :alt="item.title"
        />
        <span class="support-resources__text">
          <strong>{{ item.title }}</strong>
          <span v-if="item.priceCents !== null" class="support-resources__price"
            ><PriceText :cents="item.priceCents" />
            <small>查询时价格</small></span
          >
          <small
            >{{ item.detail
            }}<span v-if="item.experience"> · 体验来源</span></small
          >
        </span>
        <span class="support-resources__arrow" aria-hidden="true">→</span>
      </RouterLink>
    </li>
  </ul>
</template>
<style scoped>
.support-resources {
  list-style: none;
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 14px 0 0;
}
.support-resources a {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 54px;
  padding: 10px;
  border: 1px solid var(--mm-border);
  border-radius: 12px;
  background: var(--mm-white);
  color: var(--mm-ink);
  text-decoration: none;
}
.support-resources a:hover {
  border-color: var(--mm-primary);
  background: var(--mm-warm);
}
.support-resources a:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: 3px;
}
.support-resources :deep(img) {
  width: 62px;
  height: 62px;
  object-fit: cover;
  border-radius: 8px;
  flex: none;
}
.support-resources__text {
  min-width: 0;
  display: grid;
  gap: 4px;
  flex: 1;
  overflow-wrap: anywhere;
}
.support-resources strong {
  font-size: 14px;
  line-height: 1.5;
}
.support-resources small {
  font-size: 12px;
  color: var(--mm-muted);
  line-height: 1.6;
}
.support-resources__price {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.support-resources__arrow {
  color: var(--mm-primary);
  flex: none;
}
</style>
