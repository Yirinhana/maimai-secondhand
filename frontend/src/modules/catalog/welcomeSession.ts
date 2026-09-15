const WELCOME_SEEN_KEY = 'maimai-welcome-seen-v1';
let seenInMemory = false;

export function hasSeenWelcome(): boolean {
  if (seenInMemory) return true;
  try {
    return sessionStorage.getItem(WELCOME_SEEN_KEY) === '1';
  } catch {
    return false;
  }
}

export function rememberWelcome(): void {
  seenInMemory = true;
  try {
    sessionStorage.setItem(WELCOME_SEEN_KEY, '1');
  } catch {
    // A blocked storage area must not prevent entering the marketplace.
  }
}
