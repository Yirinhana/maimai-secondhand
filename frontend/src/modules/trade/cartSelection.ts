const TTL = 24 * 60 * 60 * 1000;
export function cartStorage(): Pick<Storage, "getItem" | "setItem"> {
  try {
    return window.sessionStorage;
  } catch {
    return { getItem: () => null, setItem: () => {} };
  }
}
export function readCartSelection(
  storage: Pick<Storage, "getItem">,
  owner: number,
  now = Date.now(),
): number[] {
  try {
    const value = JSON.parse(
      storage.getItem(`maimai:cart-selection:${owner}`) || "null",
    );
    if (
      !value ||
      !Array.isArray(value.ids) ||
      !Number.isFinite(value.savedAt) ||
      now - value.savedAt > TTL ||
      now < value.savedAt
    )
      return [];
    return value.ids
      .filter(
        (id: unknown) =>
          typeof id === "number" && Number.isSafeInteger(id) && id > 0,
      )
      .slice(0, 200);
  } catch {
    return [];
  }
}
export function saveCartSelection(
  storage: Pick<Storage, "setItem">,
  owner: number,
  ids: number[],
  now = Date.now(),
) {
  try {
    storage.setItem(
      `maimai:cart-selection:${owner}`,
      JSON.stringify({ ids: ids.slice(0, 200), savedAt: now }),
    );
  } catch {
    /* Selection still works when browser storage is unavailable. */
  }
}
