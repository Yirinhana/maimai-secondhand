<template>
  <section class="mm-card">
    <header v-if="title" class="mm-card__header">
      <h2 class="mm-card__title">{{ title }}</h2>
      <slot name="extra" />
    </header>
    <div class="mm-card__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{ title?: string }>()
</script>

<style scoped>
.mm-card {
  background-color: var(--mm-white);
  border: 1px solid var(--mm-border);
  border-radius: var(--mm-radius-l);
  box-shadow: var(--mm-shadow);
}

.mm-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--mm-space-4) var(--mm-space-5) 0;
}

.mm-card__title {
  font-size: var(--mm-font-l);
  font-weight: 700;
}

.mm-card__body {
  padding: var(--mm-space-4) var(--mm-space-5);
}
</style>
