import assert from 'node:assert/strict';
import { test, beforeEach } from 'node:test';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

async function moduleAt(path, replacements = {}) {
  let source = ts.transpileModule(
    await readFile(new URL(path, import.meta.url), 'utf8'),
    {
      compilerOptions: {
        target: ts.ScriptTarget.ES2022,
        module: ts.ModuleKind.ESNext,
      },
    },
  ).outputText;
  source = source.replace(
    /from (['"])(.*?)\1/g,
    (match, _quote, specifier) =>
      specifier in replacements
        ? `from ${JSON.stringify(replacements[specifier])}`
        : match,
  );
  const url =
    'data:text/javascript;base64,' +
    Buffer.from(source).toString('base64');
  return { url, exports: await import(url) };
}
const errors = await moduleAt('../src/shared/requestErrors.ts');
const api = (
  await moduleAt('../src/shared/api.ts', {
    './requestErrors': errors.url,
  })
).exports;
const { checkoutAttempt } = (
  await moduleAt('../src/modules/trade/checkoutAttempt.ts')
).exports;
const { readConversationDraft, saveConversationDraft } = (
  await moduleAt('../src/modules/messaging/conversationDraft.ts')
).exports;
const { readNearbyOrigin, saveNearbyOrigin } = (
  await moduleAt('../src/modules/catalog/nearbySearch.ts')
).exports;
let values;
beforeEach(() => {
  values = new Map();
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { onLine: true },
  });
  globalThis.sessionStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
  globalThis.document = { cookie: 'XSRF-TOKEN=local-test' };
  globalThis.history = {
    state: { back: '/cart', position: 3 },
    replaceState(next) {
      this.state = next;
    },
  };
  globalThis.window = {
    location: { pathname: '/orders', search: '?status=PAID' },
  };
});
test('offline errors explain how to resume without discarding input', async () => {
  navigator.onLine = false;
  globalThis.fetch = async () => {
    throw new TypeError('Failed to fetch');
  };
  await assert.rejects(
    api.post('/checkout', {}),
    (e) => e.code === 'OFFLINE' && e.message.includes('保留'),
  );
});
test('mutation timeout is uncertain and is never automatically retried', async () => {
  let calls = 0;
  globalThis.fetch = async () => {
    calls++;
    throw new DOMException('timeout', 'TimeoutError');
  };
  await assert.rejects(
    api.post('/checkout', {}),
    (e) => e.code === 'TIMEOUT' && e.message.includes('确认'),
  );
  assert.equal(calls, 1);
});
test('structured server errors retain HTTP status for uncertain order handling', async () => {
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({ code: 'INTERNAL_ERROR', message: '服务暂时繁忙' }),
      { status: 503 },
    );
  await assert.rejects(
    api.post('/checkout', {}),
    (e) => e.httpStatus === 503 && e.code === 'INTERNAL_ERROR',
  );
});
test('HTML gateway failures never display raw server HTML', async () => {
  globalThis.fetch = async () =>
    new Response('<h1>nginx 502 internal host</h1>', { status: 502 });
  await assert.rejects(
    api.get('/products'),
    (e) => e.message === '服务暂时繁忙，请稍后重试。',
  );
});
test('network interruption while reading the response body is normalized', async () => {
  globalThis.fetch = async () => ({
    text: async () => {
      throw new TypeError('socket dropped');
    },
  });
  await assert.rejects(
    api.get('/products'),
    (e) => e.code === 'NETWORK_ERROR',
  );
});
test('structured validation errors retain the business message and trace identifier', async () => {
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        code: 'OUT_OF_STOCK',
        message: '库存不足，请调整数量',
        traceId: 'test-only',
      }),
      { status: 409 },
    );
  await assert.rejects(
    api.post('/checkout', {}),
    (e) => e.code === 'OUT_OF_STOCK' && e.traceId === 'test-only',
  );
});
test('parallel mutations share CSRF initialization but both perform their own operation', async () => {
  document.cookie = '';
  let csrf = 0,
    writes = 0;
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/auth/csrf')) {
      csrf++;
      await new Promise((resolve) => setTimeout(resolve, 10));
      document.cookie = 'XSRF-TOKEN=token';
      return new Response('{"token":"token"}');
    }
    assert.equal(options.headers['X-XSRF-TOKEN'], 'token');
    writes++;
    return new Response('{}');
  };
  await Promise.all([api.post('/one', {}), api.post('/two', {})]);
  assert.equal(csrf, 1);
  assert.equal(writes, 2);
});
test('checkout retries and reload retain key and Vue Router history metadata', () => {
  const key = checkoutAttempt(1, 'item-A');
  assert.equal(checkoutAttempt(1, 'item-A'), key);
  assert.equal(history.state.position, 3);
  assert.equal(history.state.back, '/cart');
});
test('checkout key is not reused for another account or item set', () => {
  const first = checkoutAttempt(1, 'A');
  assert.notEqual(checkoutAttempt(2, 'A'), first);
  const second = checkoutAttempt(2, 'A');
  assert.notEqual(checkoutAttempt(2, 'B'), second);
});
test('new browser history entry means a new intentional order attempt', () => {
  const key = checkoutAttempt(1, 'A');
  history.state = { position: 4 };
  assert.notEqual(checkoutAttempt(1, 'A'), key);
});
test('drafts and pending delivery keys are scoped by both account and conversation', () => {
  const pending = {
    body: '请问还在吗？',
    clientId: crypto.randomUUID(),
    productId: 8,
  };
  saveConversationDraft('1:10', { text: pending.body, pending });
  assert.deepEqual(readConversationDraft('1:10').pending, pending);
  assert.equal(readConversationDraft('2:10').text, '');
  assert.equal(readConversationDraft('1:11').text, '');
});
test('cleared draft removes stored private content', () => {
  saveConversationDraft('1:10', { text: '草稿', pending: null });
  saveConversationDraft('1:10', { text: '', pending: null });
  assert.equal(values.size, 0);
});
test('expired or malformed private drafts are not restored', () => {
  values.set(
    'maimai-message-draft-v1:1:10',
    JSON.stringify({ text: '旧消息', savedAt: Date.now() - 25 * 3600000 }),
  );
  assert.equal(readConversationDraft('1:10').text, '');
  values.set('maimai-message-draft-v1:1:10', '{broken');
  assert.equal(readConversationDraft('1:10').text, '');
});
test('invalid pending delivery metadata cannot be retried as a valid message', () => {
  values.set(
    'maimai-message-draft-v1:1:10',
    JSON.stringify({
      text: '草稿',
      savedAt: Date.now(),
      pending: { body: 'x', clientId: 'bad', productId: -1 },
    }),
  );
  assert.equal(readConversationDraft('1:10').pending, null);
});
test('storage unavailable does not break composing, searching, or checkout', () => {
  sessionStorage.setItem = sessionStorage.getItem = () => {
    throw Error('storage denied');
  };
  history.replaceState = () => {
    throw Error('storage denied');
  };
  assert.doesNotThrow(() =>
    saveConversationDraft('1:1', { text: '仍可填写', pending: null }),
  );
  assert.equal(readConversationDraft('1:1').text, '');
  assert.equal(readNearbyOrigin(1), null);
  assert.match(checkoutAttempt(1, 'A'), /^[\da-f-]{36}$/);
});
test('nearby location survives a return visit but is isolated between accounts and clearable', () => {
  const origin = {
    latitude: 31.23,
    longitude: 121.47,
    label: '上海人民广场',
  };
  saveNearbyOrigin(1, origin);
  assert.deepEqual(readNearbyOrigin(1), origin);
  assert.equal(readNearbyOrigin(2), null);
  saveNearbyOrigin(1, null);
  assert.equal(readNearbyOrigin(1), null);
});
test('nearby search rejects stale, impossible, or future-dated location data', () => {
  for (const [age, latitude] of [
    [31 * 60000, 31],
    [0, 100],
    [-60000, 31],
  ]) {
    values.set(
      'maimai-nearby-origin-v1:1',
      JSON.stringify({
        savedAt: Date.now() - age,
        origin: { latitude, longitude: 120, label: 'test' },
      }),
    );
    assert.equal(readNearbyOrigin(1), null);
  }
});
