<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    class="mm-icon"
  >
    <path v-for="(path, i) in paths[name] ?? paths.box" :key="i" :d="path" />
  </svg>
</template>
<script setup lang="ts">
defineProps<{ name: string }>();
const paths: Record<string, string[]> = {
  search: ['M21 21l-5-5', 'M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0'],
  cart: ['M2 3h3l3 12h11l3-9H6', 'M9 20h.01M18 20h.01'],
  menu: ['M4 6h16M4 12h16M4 18h16'],
  chevron: ['m8 10 4 4 4-4'],
  arrow: ['M4 12h16m-6-6 6 6-6 6'],
  box: ['m12 3 9 5v9l-9 5-9-5V8l9-5Z', 'm3 8 9 5 9-5M12 13v9M7 5.7l9 5'],
  message: [
    'M21 11a9 9 0 0 1-9 9 10 10 0 0 1-4-.9L3 21l1.8-5A9 9 0 1 1 21 11Z',
    'M8 11h.01M12 11h.01M16 11h.01',
  ],
  plus: ['M12 5v14M5 12h14'],
  user: ['M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0', 'M4 21v-2a8 8 0 0 1 16 0v2'],
  bag: ['M5 7h14l1 14H4L5 7Z', 'M8 7V5a4 4 0 0 1 8 0v2'],
  shield: ['m12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6l9-4Z', 'm8 12 3 3 5-6'],
  book: [
    'M4 3h7a3 3 0 0 1 3 3v15a4 4 0 0 0-4-2H4V3Z',
    'M14 6a3 3 0 0 1 3-3h4v16h-3a4 4 0 0 0-4 2',
  ],
  grid: ['M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z'],
  pin: [
    'M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0Z',
    'M15 10a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  ],
  heart: [
    'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z',
  ],
};
</script>
<style scoped>
.mm-icon {
  width: 22px;
  height: 22px;
  flex: none;
  vertical-align: middle;
}
</style>
