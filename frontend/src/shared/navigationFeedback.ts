import { reactive } from 'vue';

export const navigationFeedback = reactive({
  loading: false,
  failedPath: '',
});
let timer: ReturnType<typeof setTimeout> | undefined;
export function startNavigation() {
  clearTimeout(timer);
  navigationFeedback.failedPath = '';
  timer = setTimeout(() => {
    navigationFeedback.loading = true;
  }, 180);
}
export function finishNavigation() {
  clearTimeout(timer);
  navigationFeedback.loading = false;
}
export function failNavigation(path: string) {
  finishNavigation();
  navigationFeedback.failedPath =
    path.startsWith('/') && !path.startsWith('//') ? path : '/';
}
