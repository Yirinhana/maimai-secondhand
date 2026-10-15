import { onMounted, onBeforeUnmount, type Ref } from 'vue';

/** Only transient controls opt in. Draft editors and reading sections stay open. */
export function useDismissiblePanel(
  root: Ref<HTMLElement | null>,
  isOpen: () => boolean,
  close: (restoreFocus: boolean) => void,
) {
  function outside(event: Event) {
    if (!isOpen() || !root.value || !(event.target instanceof Node)) return;
    // A confirmation dialog belongs to the current action, not the page behind it.
    if (document.querySelector('dialog[open]')) return;
    if (!root.value.contains(event.target)) close(false);
  }
  function escape(event: KeyboardEvent) {
    if (event.key !== 'Escape' || event.defaultPrevented || !isOpen()) return;
    if (!root.value?.contains(event.target as Node)) return;
    if (document.querySelector('dialog[open]')) return;
    if (
      CSS.supports('selector(select:open)') &&
      document.querySelector('select:open')
    )
      return;
    event.preventDefault();
    event.stopPropagation();
    close(true);
  }
  onMounted(() => {
    document.addEventListener('pointerdown', outside);
    document.addEventListener('focusin', outside);
    document.addEventListener('keydown', escape);
  });
  onBeforeUnmount(() => {
    document.removeEventListener('pointerdown', outside);
    document.removeEventListener('focusin', outside);
    document.removeEventListener('keydown', escape);
  });
}
