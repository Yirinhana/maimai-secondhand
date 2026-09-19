export interface PendingText {
  clientId: string;
  body: string;
  productId?: number;
}
export interface ConversationDraft {
  text: string;
  pending: PendingText | null;
}
const prefix = 'maimai-message-draft-v1:';
const maxAge = 24 * 60 * 60 * 1000;
export function readConversationDraft(owner: string): ConversationDraft {
  const empty = { text: '', pending: null };
  if (!owner) return empty;
  try {
    const data = JSON.parse(
      sessionStorage.getItem(prefix + owner) || 'null',
    );
    if (
      !data ||
      typeof data.text !== 'string' ||
      data.text.length > 2000 ||
      !Number.isFinite(data.savedAt) ||
      Date.now() - data.savedAt > maxAge
    )
      return empty;
    const p = data.pending;
    const pending =
      p &&
      typeof p.clientId === 'string' &&
      /^[\da-f-]{36}$/i.test(p.clientId) &&
      typeof p.body === 'string' &&
      p.body.length > 0 &&
      p.body.length <= 2000 &&
      (p.productId === undefined ||
        (Number.isSafeInteger(p.productId) && p.productId > 0))
        ? { clientId: p.clientId, body: p.body, productId: p.productId }
        : null;
    return { text: pending?.body ?? data.text, pending };
  } catch {
    return empty;
  }
}
export function saveConversationDraft(
  owner: string,
  data: ConversationDraft,
) {
  if (!owner) return;
  try {
    if (!data.text && !data.pending)
      sessionStorage.removeItem(prefix + owner);
    else
      sessionStorage.setItem(
        prefix + owner,
        JSON.stringify({ ...data, savedAt: Date.now() }),
      );
  } catch {
    /* Storage is optional; the current input remains usable. */
  }
}
