// A reload of this history entry must not turn an uncertain response into a new order.
// New navigations get a new history entry. Account and item scope prevent reuse elsewhere.
export function checkoutAttempt(userId: number, scope: string): string {
  const state = history.state ?? {};
  const saved = state.maimaiCheckout;
  if (
    saved?.userId === userId &&
    saved?.scope === scope &&
    typeof saved.key === 'string' &&
    /^[\da-f-]{36}$/i.test(saved.key)
  )
    return saved.key;
  const key = crypto.randomUUID();
  try {
    history.replaceState(
      { ...state, maimaiCheckout: { userId, scope, key } },
      '',
    );
  } catch {
    /* In-memory retry still reuses this key. */
  }
  return key;
}
