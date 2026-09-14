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
  close: ['m6 6 12 12M18 6 6 18'],
  check: ['m5 12 4 4L19 6'],
  refresh: [
    'M20 7v5h-5',
    'M4 17v-5h5',
    'M5 8a7 7 0 0 1 12-3l3 3M4 16l3 3a7 7 0 0 0 12-3',
  ],
  eye: [
    'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z',
    'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  ],
  'eye-off': [
    'm3 3 18 18',
    'M10 5.2a10 10 0 0 1 2-.2c6.5 0 10 7 10 7a19 19 0 0 1-3.1 3.9M6 6.4A20 20 0 0 0 2 12s3.5 7 10 7a11 11 0 0 0 5-1.3',
    'M9.9 9.9a3 3 0 0 0 4.2 4.2',
  ],
  phone: [
    'M7 2h10a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1Z',
    'M10 5h4M11 19h2',
  ],
  shirt: ['m8 3-5 3 2 5 3-1v11h8V10l3 1 2-5-5-3c0 4-8 4-8 0Z'],
  home: ['m3 10 9-7 9 7M5 9v12h14V9M10 21v-7h4v7'],
  sport: [
    'm4 20 5-5',
    'M18.8 3.8c3 3 1.4 6.2-1.5 9.1-2.8 2.8-6 3.4-8.2 1.2s-1.6-5.4 1.2-8.2c2.9-2.9 6.1-4.1 8.5-2.1Z',
    'm9 7 8 7M12 4l8 7M8 10l6 6M12 6l-4 4M17 5l-7 8M19 8l-6 7',
  ],
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
