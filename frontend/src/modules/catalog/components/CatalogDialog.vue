<template>
  <dialog
    ref="dialog"
    class="catalog-dialog"
    :class="{ 'catalog-dialog--wide': wide }"
    :aria-labelledby="`${id}-title`"
    @cancel.prevent="requestClose"
    @click.self="requestClose"
    @keydown="keepFocusInside"
  >
    <div class="catalog-dialog__surface">
      <header>
        <h2 :id="`${id}-title`">{{ title }}</h2>
        <button
          type="button"
          :aria-label="`关闭${title}`"
          @click="requestClose"
        >
          <span aria-hidden="true">×</span>
        </button>
      </header>
      <slot />
    </div>
  </dialog>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
const props = defineProps<{
  open: boolean;
  title: string;
  id: string;
  wide?: boolean;
}>();
const emit = defineEmits<{ 'update:open': [value: boolean] }>();
const dialog = ref<HTMLDialogElement | null>(null);
let previousFocus: HTMLElement | null = null;
function requestClose() {
  emit('update:open', false);
}
function keepFocusInside(event: KeyboardEvent) {
  if (event.key !== 'Tab' || !dialog.value) return;
  const controls = Array.from(
    dialog.value.querySelectorAll<HTMLElement>(
      'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((element) => element.getClientRects().length > 0);
  const first = controls[0];
  const last = controls[controls.length - 1];
  if (!first || !last) {
    event.preventDefault();
    dialog.value.focus();
    return;
  }
  if (
    event.shiftKey &&
    (document.activeElement === first ||
      !dialog.value.contains(document.activeElement))
  ) {
    event.preventDefault();
    last.focus();
  } else if (
    !event.shiftKey &&
    (document.activeElement === last ||
      !dialog.value.contains(document.activeElement))
  ) {
    event.preventDefault();
    first.focus();
  }
}
function close() {
  if (!dialog.value?.open) return;
  dialog.value.close();
  if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true });
  previousFocus = null;
}
watch(
  () => props.open,
  async (open) => {
    await nextTick();
    if (open !== props.open || !dialog.value) return;
    if (open && !dialog.value.open) {
      previousFocus =
        document.activeElement instanceof HTMLElement
          ? document.activeElement
          : null;
      dialog.value.showModal();
      const target = dialog.value.querySelector<HTMLElement>('[autofocus]');
      target?.focus();
    } else if (!open) close();
  },
  { immediate: true },
);
onBeforeUnmount(close);
</script>

<style scoped>
.catalog-dialog {
  position: fixed;
  inset: 0;
  margin: auto;
  width: min(440px, calc(100% - 32px));
  max-width: none;
  max-height: calc(100dvh - 40px);
  padding: 0;
  border: 1px solid var(--mm-border);
  border-radius: 14px;
  background: var(--mm-white);
  color: var(--mm-ink);
  box-shadow: 0 24px 100px #201b1933;
  overflow: auto;
  overscroll-behavior: contain;
}
.catalog-dialog--wide {
  width: min(1080px, calc(100% - 32px));
}
.catalog-dialog::backdrop {
  background: #171716ba;
}
.catalog-dialog__surface {
  padding: 24px;
}
.catalog-dialog header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}
.catalog-dialog h2 {
  margin: 4px 0 0;
  font-size: 20px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}
.catalog-dialog header button {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border: 1px solid var(--mm-border);
  border-radius: 50%;
  background: var(--mm-white);
  color: var(--mm-ink);
  font-size: 25px;
  line-height: 1;
}
.catalog-dialog header button:focus-visible {
  outline: 2px solid var(--mm-primary);
  outline-offset: 3px;
}
@media (max-width: 600px) {
  .catalog-dialog__surface {
    padding: 18px;
  }
  .catalog-dialog h2 {
    font-size: 18px;
  }
}
</style>
