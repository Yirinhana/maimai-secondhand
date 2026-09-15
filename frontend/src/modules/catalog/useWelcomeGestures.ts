import { onMounted, onBeforeUnmount, type Ref } from 'vue';

/** One physical gesture advances one scene; short viewports retain native scrolling. */
export function useWelcomeGestures(
  root: Ref<HTMLElement | null>,
  blocked: () => boolean,
  next: () => void,
  previous: () => void,
) {
  const interactive =
    'a,button,input,textarea,select,[contenteditable="true"],[role="button"]';
  let start: { x: number; y: number } | null = null;
  let dragged = false;
  let wheelTotal = 0;
  let wheelUsed = false;
  let wheelIdle: ReturnType<typeof setTimeout> | undefined;
  function pointerDown(event: PointerEvent) {
    start =
      event.isPrimary && event.button === 0
        ? { x: event.clientX, y: event.clientY }
        : null;
    dragged = false;
  }
  function pointerMove(event: PointerEvent) {
    if (
      start &&
      Math.hypot(event.clientX - start.x, event.clientY - start.y) > 12
    )
      dragged = true;
  }
  function pointerCancel() {
    start = null;
  }
  function click(event: MouseEvent) {
    const wasTap = event.detail === 0 || (start && !dragged);
    start = null;
    if (
      !wasTap ||
      blocked() ||
      event.button !== 0 ||
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      event.shiftKey
    )
      return;
    if (
      (event.target instanceof Element && event.target.closest(interactive)) ||
      window.getSelection()?.toString()
    )
      return;
    next();
  }
  function keyboard(event: KeyboardEvent) {
    if (
      event.ctrlKey ||
      event.metaKey ||
      event.altKey ||
      event.shiftKey ||
      (event.target instanceof Element && event.target.closest(interactive))
    )
      return;
    if (
      ![
        'ArrowDown',
        'ArrowRight',
        'ArrowUp',
        'ArrowLeft',
        ' ',
        'Enter',
      ].includes(event.key)
    )
      return;
    event.preventDefault();
    if (blocked() || event.repeat) return;
    if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') previous();
    else next();
  }
  function wheel(event: WheelEvent) {
    if (
      event.ctrlKey ||
      event.metaKey ||
      event.shiftKey ||
      Math.abs(event.deltaY) <= Math.abs(event.deltaX)
    )
      return;
    if (wheelIdle) clearTimeout(wheelIdle);
    wheelIdle = setTimeout(() => {
      wheelUsed = false;
      wheelTotal = 0;
    }, 280);
    const container = root.value;
    if (!container) return;
    const canScroll =
      event.deltaY > 0
        ? container.scrollTop + container.clientHeight <
          container.scrollHeight - 2
        : container.scrollTop > 2;
    if (canScroll && !blocked()) {
      // Reaching a text area's edge must not also advance the scene in the same gesture.
      wheelUsed = true;
      return;
    }
    event.preventDefault();
    if (blocked()) {
      wheelUsed = true;
      return;
    }
    if (wheelUsed) return;
    const delta =
      event.deltaY *
      (event.deltaMode === 1
        ? 16
        : event.deltaMode === 2
          ? container.clientHeight
          : 1);
    if (Math.sign(delta) !== Math.sign(wheelTotal)) wheelTotal = 0;
    wheelTotal += delta;
    if (Math.abs(wheelTotal) < 60) return;
    wheelUsed = true;
    if (wheelTotal > 0) next();
    else previous();
  }
  onMounted(() =>
    root.value?.addEventListener('wheel', wheel, { passive: false }),
  );
  onBeforeUnmount(() => {
    root.value?.removeEventListener('wheel', wheel);
    if (wheelIdle) clearTimeout(wheelIdle);
  });
  return { pointerDown, pointerMove, pointerCancel, click, keyboard };
}
