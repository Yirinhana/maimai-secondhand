import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

// Lists retain their place in the URL, including browser Back and shared links.
export function useListQuery(allowedStatuses: readonly string[] = []) {
  const route = useRoute(),
    router = useRouter();
  const page = computed(() => {
    const value =
      typeof route.query.page === 'string' ? Number(route.query.page) : 0;
    return Number.isSafeInteger(value) && value >= 0 && value <= 10000
      ? value
      : 0;
  });
  const status = computed(() =>
    typeof route.query.status === 'string' &&
    allowedStatuses.includes(route.query.status)
      ? route.query.status
      : '',
  );
  function navigate(nextPage: number, nextStatus = status.value) {
    return router.push({
      path: route.path,
      query: {
        ...route.query,
        page: nextPage > 0 ? String(nextPage) : undefined,
        status: nextStatus || undefined,
      },
    });
  }
  return {
    page,
    status,
    setPage: (value: number) => navigate(value),
    setStatus: (value: string) => navigate(0, value),
  };
}
