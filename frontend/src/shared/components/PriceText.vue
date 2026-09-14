<template>
  <span class="mm-price">{{ formatPrice(cents) }}</span>
</template>

<script setup lang="ts">
import { formatPrice } from '../format'

defineProps<{ cents: number }>()
</script>

<style scoped>
.mm-price {
  color: var(--mm-danger);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
</style>
