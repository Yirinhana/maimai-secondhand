import { onMounted, onUnmounted, type Ref } from 'vue';

export function useHomeMotion(root: Ref<HTMLElement | null>) {
  let observer: IntersectionObserver | undefined;
  let motion: MediaQueryList | undefined;
  const revealAll = () => {
    if (!motion?.matches) return;
    observer?.disconnect();
    root.value?.querySelectorAll('.is-waiting').forEach((el) => {
      el.classList.remove('is-waiting');
    });
  };

  onMounted(() => {
    motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    motion.addEventListener('change', revealAll);
    if (motion.matches || !('IntersectionObserver' in window)) return;

    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          entry.target.classList.remove('is-waiting');
          observer?.unobserve(entry.target);
        }
      },
      { threshold: 0.08, rootMargin: '0px 0px -32px 0px' },
    );
    root.value?.querySelectorAll<HTMLElement>('[data-reveal]').forEach((el) => {
      // Preserve restored scroll positions and already visible content.
      if (el.getBoundingClientRect().top < window.innerHeight - 32) return;
      el.classList.add('is-waiting');
      observer?.observe(el);
    });
  });

  onUnmounted(() => {
    observer?.disconnect();
    motion?.removeEventListener('change', revealAll);
  });
}

export function scrollToSection(event: MouseEvent, id: string) {
  if (
    event.button !== 0 ||
    event.ctrlKey ||
    event.metaKey ||
    event.shiftKey ||
    event.altKey
  )
    return;
  const target = document.getElementById(id);
  if (!target) return;
  event.preventDefault();
  target.focus({ preventScroll: true });
  target.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ? 'instant'
      : 'smooth',
    block: 'start',
  });
}
