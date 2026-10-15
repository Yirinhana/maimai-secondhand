import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue';

const storageKey = 'maimai-maizai-dock-v1';
type Position = { edge: 'left' | 'right'; ratio: number };
const initial = (): Position => ({ edge: 'right', ratio: 1 });
const clamp = (value: number, min: number, max: number) =>
  Math.max(min, Math.min(max, value));

/** Free pointer movement while held; only the final side-edge position is saved. */
export function useEdgeDock(launcher: Ref<HTMLButtonElement | null>) {
  const position = ref<Position>(initial());
  const coordinates = ref<{ left: number; top: number } | null>(null);
  const dragging = ref(false);
  const snapping = ref(false);
  let snapTimer: ReturnType<typeof setTimeout> | undefined;
  let pointer: {
    id: number;
    x: number;
    y: number;
    offsetX: number;
    offsetY: number;
    interruptedSnap: boolean;
    original: Position;
  } | null = null;
  let suppressClick = false;
  let observer: ResizeObserver | undefined;
  let contentObserver: MutationObserver | undefined;
  let resizeFrame = 0;
  const style = computed(() =>
    coordinates.value
      ? {
          left: coordinates.value.left + 'px',
          top: coordinates.value.top + 'px',
          right: 'auto',
          bottom: 'auto',
        }
      : {},
  );

  function bounds() {
    const element = launcher.value;
    if (!element) return null;
    const css = getComputedStyle(element);
    const visual = window.visualViewport;
    const scale = Math.max(1, visual?.scale || 1);
    const safe = (side: string) =>
      Math.max(
        12,
        parseFloat(css.getPropertyValue('--dock-safe-' + side)) || 0,
      ) / scale;
    // Keep the edge gap constant on screen while leaving the button itself
    // magnifiable. Otherwise a pinch zoom pushes the dock visibly inward.
    // VisualViewport coordinates and pointer/client rects share layout CSS
    // pixels. Do not multiply these by scale: pinch zoom already reduces the
    // visual width/height. Ignore transient zero sizes during browser UI changes.
    const width = Math.min(
      document.documentElement.clientWidth,
      visual && visual.width > 0 ? visual.width : innerWidth,
    );
    const height = visual && visual.height > 0 ? visual.height : innerHeight;
    const left = Math.max(0, visual?.offsetLeft ?? 0);
    const top = Math.max(0, visual?.offsetTop ?? 0);
    return {
      minX: left + safe('left'),
      maxX: Math.max(
        left + safe('left'),
        left + width - element.offsetWidth - safe('right'),
      ),
      minY: top + safe('top'),
      maxY: Math.max(
        top + safe('top'),
        top + height - element.offsetHeight - safe('bottom'),
      ),
      middle: left + width / 2,
      width: element.offsetWidth,
      height: element.offsetHeight,
    };
  }
  function place() {
    const area = bounds();
    if (!area) return;
    if (pointer && coordinates.value) {
      coordinates.value = {
        left: clamp(coordinates.value.left, area.minX, area.maxX),
        top: clamp(coordinates.value.top, area.minY, area.maxY),
      };
      return;
    }
    const left = position.value.edge === 'left' ? area.minX : area.maxX;
    const preferred =
      area.minY + (area.maxY - area.minY) * position.value.ratio;
    // Keep the user's saved edge, but make room for visible page actions. This
    // temporary adjustment is never persisted over the user's drag preference.
    const obstacles = [
      ...document.querySelectorAll<HTMLElement>(
        'main button, main a, main input:not([type="hidden"]), main select, main textarea, main summary, .mm-back-top',
      ),
    ]
      .filter(
        (e) =>
          e.getClientRects().length &&
          getComputedStyle(e).visibility !== 'hidden',
      )
      .map((e) => e.getBoundingClientRect())
      .filter(
        (r) =>
          r.bottom > area.minY &&
          r.top < area.maxY + area.height &&
          left < r.right + 4 &&
          left + area.width > r.left - 4,
      );
    const overlap = (top: number) =>
      obstacles.reduce(
        (sum, r) =>
          sum +
          Math.max(
            0,
            Math.min(top + area.height, r.bottom + 4) -
              Math.max(top, r.top - 4),
          ),
        0,
      );
    const candidates = [
      preferred,
      area.minY,
      area.maxY,
      ...obstacles.flatMap((r) => [r.top - area.height - 4, r.bottom + 4]),
    ].map((top) => clamp(top, area.minY, area.maxY));
    candidates.sort(
      (a, b) =>
        overlap(a) - overlap(b) ||
        Math.abs(a - preferred) - Math.abs(b - preferred),
    );
    coordinates.value = { left, top: candidates[0] ?? preferred };
  }
  function persist() {
    try {
      localStorage.setItem(storageKey, JSON.stringify(position.value));
    } catch {
      /* Device storage is optional. */
    }
  }
  function schedulePlace() {
    if (resizeFrame) return;
    resizeFrame = requestAnimationFrame(() => {
      resizeFrame = 0;
      place();
    });
  }
  function fitViewport() {
    // A snap destination belongs to the previous viewport. On pinch/pan or
    // rotation, reposition immediately instead of animating through offscreen
    // coordinates for another 460ms.
    stopSnap();
    schedulePlace();
  }
  function stopSnap() {
    if (snapTimer) clearTimeout(snapTimer);
    snapping.value = false;
  }
  function settle(animate: boolean) {
    stopSnap();
    snapping.value = animate;
    place();
    if (animate) snapTimer = setTimeout(stopSnap, 480);
  }
  function down(event: PointerEvent) {
    if (!event.isPrimary || event.button !== 0 || pointer) return;
    const element = launcher.value;
    if (!element) return;
    const rect = element.getBoundingClientRect();
    const interruptedSnap = snapping.value;
    // Freeze the visible position, not the previous animation's destination.
    stopSnap();
    coordinates.value = { left: rect.left, top: rect.top };
    suppressClick = false;
    pointer = {
      id: event.pointerId,
      x: event.clientX,
      y: event.clientY,
      offsetX: event.clientX - rect.left,
      offsetY: event.clientY - rect.top,
      interruptedSnap,
      original: { ...position.value },
    };
    launcher.value?.setPointerCapture(event.pointerId);
  }
  function move(event: PointerEvent) {
    if (!pointer || pointer.id !== event.pointerId) return;
    if (
      !dragging.value &&
      Math.hypot(event.clientX - pointer.x, event.clientY - pointer.y) < 8
    )
      return;
    dragging.value = true;
    suppressClick = true;
    const area = bounds();
    if (!area) return;
    coordinates.value = {
      left: clamp(event.clientX - pointer.offsetX, area.minX, area.maxX),
      top: clamp(event.clientY - pointer.offsetY, area.minY, area.maxY),
    };
  }
  function release(cancelled: boolean) {
    if (!pointer) return;
    const current = pointer;
    const moved = dragging.value;
    pointer = null;
    if (cancelled) position.value = current.original;
    else if (moved && coordinates.value) {
      const area = bounds();
      if (area) {
        position.value = {
          edge:
            coordinates.value.left + area.width / 2 < area.middle
              ? 'left'
              : 'right',
          ratio:
            area.maxY > area.minY
              ? clamp(
                  (coordinates.value.top - area.minY) / (area.maxY - area.minY),
                  0,
                  1,
                )
              : 0,
        };
        persist();
      }
    }
    dragging.value = false;
    if (launcher.value?.hasPointerCapture(current.id))
      launcher.value.releasePointerCapture(current.id);
    settle(moved || current.interruptedSnap);
  }
  function up(event: PointerEvent) {
    if (pointer?.id === event.pointerId) {
      move(event);
      release(false);
    }
  }
  function cancel() {
    release(true);
  }
  function click(event: MouseEvent) {
    if (!suppressClick) return true;
    suppressClick = false;
    event.preventDefault();
    event.stopPropagation();
    return false;
  }
  function keyboard(event: KeyboardEvent) {
    if (
      event.altKey ||
      event.ctrlKey ||
      event.metaKey ||
      event.shiftKey ||
      pointer
    )
      return;
    if (event.key === 'Enter' || event.key === ' ') {
      suppressClick = false;
      return;
    }
    if (
      !['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home'].includes(
        event.key,
      )
    )
      return;
    event.preventDefault();
    const area = bounds();
    if (!area) return;
    if (event.key === 'Home') position.value = initial();
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight')
      position.value.edge = event.key === 'ArrowLeft' ? 'left' : 'right';
    else
      position.value.ratio = clamp(
        position.value.ratio +
          (event.key === 'ArrowUp' ? -40 : 40) /
            Math.max(1, area.maxY - area.minY),
        0,
        1,
      );
    settle(true);
    persist();
  }
  onMounted(() => {
    try {
      const saved: unknown = JSON.parse(
        localStorage.getItem(storageKey) || 'null',
      );
      if (
        saved &&
        typeof saved === 'object' &&
        'edge' in saved &&
        'ratio' in saved &&
        (saved.edge === 'left' || saved.edge === 'right') &&
        typeof saved.ratio === 'number' &&
        Number.isFinite(saved.ratio)
      ) {
        position.value = { edge: saved.edge, ratio: clamp(saved.ratio, 0, 1) };
      }
    } catch {
      /* A malformed or unavailable preference uses the default edge. */
    }
    place();
    observer = new ResizeObserver(schedulePlace);
    if (launcher.value) observer.observe(launcher.value);
    const main = document.querySelector('main');
    if (main) {
      observer.observe(main);
      contentObserver = new MutationObserver(schedulePlace);
      contentObserver.observe(main, { childList: true, subtree: true });
    }
    window.addEventListener('resize', fitViewport);
    window.addEventListener('scroll', schedulePlace, { passive: true });
    window.addEventListener('blur', cancel);
    window.visualViewport?.addEventListener('resize', fitViewport);
    window.visualViewport?.addEventListener('scroll', fitViewport);
    schedulePlace();
  });
  onBeforeUnmount(() => {
    cancel();
    stopSnap();
    observer?.disconnect();
    contentObserver?.disconnect();
    if (resizeFrame) cancelAnimationFrame(resizeFrame);
    window.removeEventListener('resize', fitViewport);
    window.removeEventListener('scroll', schedulePlace);
    window.removeEventListener('blur', cancel);
    window.visualViewport?.removeEventListener('resize', fitViewport);
    window.visualViewport?.removeEventListener('scroll', fitViewport);
  });
  return {
    position,
    style,
    dragging,
    snapping,
    down,
    move,
    up,
    cancel,
    click,
    keyboard,
  };
}
