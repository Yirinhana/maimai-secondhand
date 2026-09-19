// Restore an in-app list's query only when it belongs to the expected list.
// Unrelated browser history and external URLs never become a return target.
export function previousListPath(fallback: string): string {
  const previous = history.state?.back;
  return typeof previous === 'string' &&
    previous.split(/[?#]/)[0] === fallback
    ? previous
    : fallback;
}
