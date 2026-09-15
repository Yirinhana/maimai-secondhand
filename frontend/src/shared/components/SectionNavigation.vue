<template>
  <section class="mm-work-nav" :aria-label="`${config.label}区域`">
    <div class="mm-work-nav__inner">
      <div class="mm-work-nav__heading">
        <span
          ><MmIcon :name="section === 'seller' ? 'box' : 'user'" />{{
            config.label
          }}</span
        ><small>{{ title }}</small>
      </div>
      <nav v-if="links.length" aria-label="分区导航">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          :class="{ 'is-active': route.path === link.to }"
          :aria-current="route.path === link.to ? 'page' : undefined"
          >{{ link.label }}</RouterLink
        >
      </nav>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { sectionInfo } from '../navigation';
import MmIcon from './MmIcon.vue';
const props = defineProps<{ section: string; title: string }>();
const route = useRoute();
const config = computed(
  () => sectionInfo[props.section] ?? sectionInfo.market!,
);
const links = computed(() =>
  props.section === 'account'
    ? config.value.links.filter((l) => ['/me', '/me/community'].includes(l.to))
    : config.value.links,
);
</script>
<style scoped>
.mm-work-nav {
  border-bottom: 1px solid var(--mm-border);
  background: #fffdf9;
}
.mm-work-nav__inner {
  max-width: 1280px;
  margin: auto;
  padding: 16px 28px 0;
}
.mm-work-nav__heading {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 10px;
}
.mm-work-nav__heading > span {
  display: flex;
  gap: 8px;
  align-items: center;
  font-weight: 700;
  font-size: 15px;
}
.mm-work-nav__heading .mm-icon {
  width: 18px;
}
.mm-work-nav__heading small {
  font-size: 12px;
  color: var(--mm-muted);
  border-left: 1px solid var(--mm-border);
  padding-left: 16px;
}
nav {
  display: flex;
  gap: 22px;
  overflow-x: auto;
}
nav a {
  flex: none;
  padding: 10px 2px 13px;
  font-size: 13px;
  color: var(--mm-muted);
  border-bottom: 2px solid transparent;
}
nav a:hover {
  color: var(--mm-primary);
  text-decoration: none;
}
nav a.is-active {
  color: var(--mm-primary);
  border-color: var(--mm-primary);
  font-weight: 700;
}
@media (max-width: 760px) {
  .mm-work-nav__inner {
    padding: 12px 16px 0;
  }
  nav {
    gap: 18px;
  }
}
</style>
