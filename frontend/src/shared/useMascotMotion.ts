import { computed, onMounted, onUnmounted, ref, watch, type Ref } from 'vue';

/** Scoped mascot motion; shared by the opening and support, without an idle JS loop. */
export function useMascotMotion(
  root: Ref<HTMLElement | null>,
  mascot: Ref<HTMLElement | null>,
) {
  const paused = ref(false);
  const reduced = ref(false);
  const hidden = ref(false);
  const greeting = ref(false);
  const animated = computed(
    () => !paused.value && !reduced.value && !hidden.value,
  );
  let motion: MediaQueryList | undefined;
  let frame = 0;
  let greetingTimer: ReturnType<typeof setTimeout> | undefined;
  let pointer = { x: 0, y: 0 };

  function reset() {
    if (frame) cancelAnimationFrame(frame);
    frame = 0;
    root.value?.style.setProperty('--look-x', '0');
    root.value?.style.setProperty('--look-y', '0');
    root.value?.style.setProperty('--drift-x', '0px');
    root.value?.style.setProperty('--drift-y', '0px');
  }
  function point(event: PointerEvent) {
    if (!animated.value) return;
    pointer = { x: event.clientX, y: event.clientY };
    if (frame) return;
    frame = requestAnimationFrame(() => {
      frame = 0;
      const bounds = mascot.value?.getBoundingClientRect();
      if (!bounds || !root.value || !animated.value) return;
      const clamp = (value: number) => Math.max(-1, Math.min(1, value));
      const x = clamp(
        (pointer.x - bounds.x - bounds.width / 2) / (innerWidth * 0.4),
      );
      const y = clamp(
        (pointer.y - bounds.y - bounds.height / 2) / (innerHeight * 0.5),
      );
      root.value.style.setProperty('--look-x', x.toFixed(3));
      root.value.style.setProperty('--look-y', y.toFixed(3));
      root.value.style.setProperty(
        '--drift-x',
        ((pointer.x / innerWidth - 0.5) * 15).toFixed(2) + 'px',
      );
      root.value.style.setProperty(
        '--drift-y',
        ((pointer.y / innerHeight - 0.5) * 12).toFixed(2) + 'px',
      );
    });
  }
  function greet() {
    if (greetingTimer) clearTimeout(greetingTimer);
    greeting.value = true;
    greetingTimer = setTimeout(() => {
      greeting.value = false;
    }, 1400);
  }
  const syncMotion = () => {
    reduced.value = Boolean(motion?.matches);
  };
  const syncVisibility = () => {
    hidden.value = document.hidden;
  };
  watch(animated, (enabled) => {
    if (!enabled) reset();
  });
  onMounted(() => {
    motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    syncMotion();
    syncVisibility();
    motion.addEventListener('change', syncMotion);
    document.addEventListener('visibilitychange', syncVisibility);
  });
  onUnmounted(() => {
    reset();
    if (greetingTimer) clearTimeout(greetingTimer);
    motion?.removeEventListener('change', syncMotion);
    document.removeEventListener('visibilitychange', syncVisibility);
  });
  return { animated, paused, reduced, greeting, point, reset, greet };
}
