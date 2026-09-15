export function waitForAnchor(hash: string, current: () => boolean) {
  return new Promise<{ el: HTMLElement; top: number } | false>((resolve) => {
    let id: string;
    try {
      id = decodeURIComponent(hash.slice(1));
    } catch {
      resolve(false);
      return;
    }
    const finish = (value: { el: HTMLElement; top: number } | false) => {
      observer.disconnect();
      window.clearTimeout(timer);
      resolve(value);
    };
    const check = () => {
      if (!current()) return finish(false);
      const el = document.getElementById(id);
      if (el) finish({ el, top: 20 });
    };
    const observer = new MutationObserver(check);
    const timer = window.setTimeout(() => finish(false), 10000);
    observer.observe(document.body, { subtree: true, childList: true });
    check();
  });
}
