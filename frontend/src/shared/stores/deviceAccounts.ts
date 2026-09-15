import { ref } from 'vue';
import type { Me } from '../types';

export type DeviceAccount = Pick<Me, 'id' | 'email' | 'nickname' | 'avatarUrl'>;
const KEY = 'maimai:device-accounts:v1';
function read(): DeviceAccount[] {
  try {
    const rows: unknown = JSON.parse(localStorage.getItem(KEY) || '[]');
    if (!Array.isArray(rows)) return [];
    return rows
      .filter(
        (a) =>
          a &&
          Number.isSafeInteger(a.id) &&
          typeof a.email === 'string' &&
          typeof a.nickname === 'string',
      )
      .slice(0, 5)
      .map((a) => ({
        id: a.id,
        email: a.email,
        nickname: a.nickname,
        avatarUrl:
          typeof a.avatarUrl === 'string' &&
          a.avatarUrl.startsWith('/') &&
          !a.avatarUrl.startsWith('//')
            ? a.avatarUrl
            : null,
      }));
  } catch {
    return [];
  }
}
export const deviceAccounts = ref<DeviceAccount[]>(read());
function persist() {
  try {
    localStorage.setItem(KEY, JSON.stringify(deviceAccounts.value));
  } catch {
    /* Login remains available without device storage. */
  }
}
export function rememberAccount(me: Me) {
  const { id, email, nickname, avatarUrl } = me;
  deviceAccounts.value = [
    { id, email, nickname, avatarUrl },
    ...deviceAccounts.value.filter((a) => a.id !== id),
  ].slice(0, 5);
  persist();
}
export function forgetAccount(id: number) {
  deviceAccounts.value = deviceAccounts.value.filter((a) => a.id !== id);
  persist();
}
export function announceAccountChange(id: number | null) {
  try {
    localStorage.setItem(
      'maimai:auth-change',
      JSON.stringify({ id, at: Date.now() }),
    );
  } catch {
    /* Cookie authentication still works. */
  }
}
window.addEventListener('storage', (event) => {
  if (event.key === KEY) deviceAccounts.value = read();
  // Other tabs must discard the previous account's store, private views and WebSocket.
  if (event.key === 'maimai:auth-change') window.location.replace('/');
});
