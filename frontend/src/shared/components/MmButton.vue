<template>
  <button
    class="mm-button"
    :class="[`mm-button--${variant}`, { 'is-loading': loading }]"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
    :type="type"
  >
    <span v-if="loading" class="mm-button__spinner" aria-hidden="true"></span>
    <span class="mm-button__label"><slot /></span>
  </button>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'primary' | 'ghost' | 'danger';
    loading?: boolean;
    disabled?: boolean;
    type?: 'button' | 'submit';
  }>(),
  { variant: 'primary', loading: false, disabled: false, type: 'button' },
);
</script>

<style scoped>
.mm-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--mm-space-2);
  min-height: 44px;
  padding: 0 var(--mm-space-4);
  border-radius: var(--mm-radius-m);
  border: 1px solid transparent;
  font-size: var(--mm-font-base);
  font-weight: 600;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.mm-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.mm-button--primary {
  background-color: var(--mm-primary);
  color: var(--mm-white);
}
.mm-button--primary:hover:not(:disabled) {
  background-color: var(--mm-primary-hover);
}
.mm-button--primary:active:not(:disabled) {
  background-color: var(--mm-primary-active);
}

.mm-button--ghost {
  background-color: var(--mm-white);
  color: var(--mm-ink);
  border-color: var(--mm-border);
}
.mm-button--ghost:hover:not(:disabled) {
  background-color: var(--mm-accent-soft);
  border-color: #d5b29b;
}

.mm-button--danger {
  background-color: var(--mm-danger);
  color: var(--mm-white);
}
.mm-button--danger:hover:not(:disabled) {
  background-color: var(--mm-danger-hover);
}

.mm-button__spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: mm-spin 0.8s linear infinite;
}

@keyframes mm-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .mm-button {
    transition: none;
  }
  .mm-button__spinner {
    animation: none;
  }
}
</style>
