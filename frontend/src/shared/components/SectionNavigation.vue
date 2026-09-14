<template>
  <section class="mm-zone" :aria-label="`${config.label}区域`">
    <div class="mm-zone__inner">
      <div class="mm-zone__heading">
        <div class="mm-zone__identity">
          <span class="mm-zone__icon"><MmIcon :name="appearance.icon" /></span>
          <div>
            <p class="mm-zone__title">{{ config.label }}</p>
            <p class="mm-zone__description">{{ appearance.description }}</p>
          </div>
        </div>
        <nav aria-label="当前位置" class="mm-breadcrumb">
          <RouterLink to="/">首页</RouterLink>
          <MmIcon name="chevron" />
          <span v-if="title !== config.label">{{ config.label }}</span>
          <MmIcon v-if="title !== config.label" name="chevron" />
          <strong aria-current="page">{{ title }}</strong>
        </nav>
      </div>
      <nav
        v-if="config.links.length"
        aria-label="分区导航"
        class="mm-section-nav"
      >
        <RouterLink
          v-for="link in config.links"
          :key="link.to"
          :to="link.to"
          :class="{ 'is-active': isActive(link.to) }"
          :aria-current="isActive(link.to) ? 'page' : undefined"
          >{{ link.label }}</RouterLink
        >
      </nav>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { sectionInfo, sectionAppearance } from '../navigation';
import MmIcon from './MmIcon.vue';

const props = defineProps<{ section: string; title: string }>();
const route = useRoute(),
  router = useRouter();
const config = computed(
  () => sectionInfo[props.section] ?? sectionInfo.market!,
);
const appearance = computed(
  () => sectionAppearance[props.section] ?? sectionAppearance.market!,
);
function isActive(to: string) {
  const target = router.resolve(to);
  if (
    ['/messages', '/official', '/support'].includes(target.path) &&
    route.path.startsWith(target.path + '/')
  )
    return true;
  return (
    target.path === route.path &&
    (target.query.mine ?? '') === (route.query.mine ?? '')
  );
}
</script>

<style scoped>
.mm-zone {
  background: var(--mm-zone-banner);
  color: var(--mm-zone-ink);
  border-bottom: 1px solid var(--mm-zone-border);
}
.mm-zone__inner {
  max-width: 1280px;
  margin: auto;
  padding: 0 28px;
}
.mm-zone__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 22px 0;
}
.mm-zone__identity {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.mm-zone__icon {
  width: 48px;
  height: 48px;
  border: 1px solid var(--mm-zone-border);
  background: var(--mm-zone-icon-bg);
  border-radius: 13px;
  display: grid;
  place-items: center;
  flex: none;
}
.mm-zone__icon .mm-icon {
  width: 24px;
  height: 24px;
}
.mm-zone__title {
  font-size: 21px;
  font-weight: 750;
  letter-spacing: 0.2px;
}
.mm-zone__description {
  margin-top: 2px;
  font-size: 12px;
  color: var(--mm-zone-muted);
}
.mm-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--mm-zone-muted);
}
.mm-breadcrumb a {
  color: inherit;
}
.mm-breadcrumb strong {
  color: var(--mm-zone-ink);
  font-weight: 600;
}
.mm-breadcrumb .mm-icon {
  width: 12px;
  height: 12px;
  transform: rotate(-90deg);
}
.mm-section-nav {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 6px;
  margin-bottom: 16px;
  background: var(--mm-white);
  border: 1px solid var(--mm-zone-border);
  border-radius: 10px;
}
.mm-section-nav > a {
  flex: none;
  display: flex;
  align-items: center;
  min-height: 40px;
  padding: 8px 18px;
  border-radius: 6px;
  color: var(--mm-muted);
  font-size: 13px;
  font-weight: 600;
}
.mm-section-nav > a:hover {
  background: var(--mm-zone-soft);
  color: var(--mm-zone-accent);
  text-decoration: none;
}
.mm-section-nav > a.is-active {
  background: var(--mm-zone-accent);
  color: white;
  box-shadow: 0 2px 4px #18232012;
}
@media (max-width: 760px) {
  .mm-zone__inner {
    padding: 0 16px;
  }
  .mm-zone__heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
    padding: 16px 0 12px;
  }
  .mm-zone__title {
    font-size: 19px;
  }
  .mm-zone__icon {
    width: 42px;
    height: 42px;
    border-radius: 10px;
  }
  .mm-zone__description {
    font-size: 11px;
  }
  .mm-breadcrumb {
    font-size: 11px;
    gap: 5px;
  }
  .mm-section-nav {
    margin-bottom: 12px;
    gap: 3px;
  }
  .mm-section-nav > a {
    padding: 9px 13px;
    font-size: 12px;
    min-height: 42px;
  }
}
</style>
