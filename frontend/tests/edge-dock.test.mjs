import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { createRenderer, shallowRef } from 'vue';
import ts from 'typescript';

// Run the real composable in Vue's renderer with deterministic viewport inputs.
// These tests verify pointer state, not browser painting or physical touch input.
const source = await readFile(
  new URL('../src/modules/support/useEdgeDock.ts', import.meta.url),
  'utf8',
);
const compiled = ts
  .transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.ESNext,
    },
  })
  .outputText.replace(
    /from ['"]vue['"]/,
    `from ${JSON.stringify(import.meta.resolve('vue'))}`,
  );
const { useEdgeDock } = await import(
  'data:text/javascript;base64,' + Buffer.from(compiled).toString('base64')
);
const renderer = createRenderer({
  createElement: () => ({}),
  createText: () => ({}),
  createComment: () => ({}),
  insert() {},
  remove() {},
  setText() {},
  setElementText() {},
  patchProp() {},
  parentNode: () => null,
  nextSibling: () => null,
});
let dispose;
afterEach(() => {
  dispose?.();
  dispose = undefined;
});

function fixture({ width = 390, height = 844, saved = new Map() } = {}) {
  const win = new EventTarget();
  win.visualViewport = Object.assign(new EventTarget(), {
    width,
    height,
    offsetLeft: 0,
    offsetTop: 0,
  });
  const frames = new Map();
  let serial = 0;
  Object.assign(globalThis, {
    window: win,
    innerWidth: width,
    innerHeight: height,
    document: {
      documentElement: { clientWidth: width },
      querySelector: () => null,
      querySelectorAll: () => [],
    },
    getComputedStyle: () => ({ getPropertyValue: () => '0px' }),
    localStorage: {
      getItem: (key) => saved.get(key) ?? null,
      setItem: (key, value) => saved.set(key, value),
    },
    ResizeObserver: class {
      observe() {}
      disconnect() {}
    },
    requestAnimationFrame: (fn) => {
      frames.set(++serial, fn);
      return serial;
    },
    cancelAnimationFrame: (id) => frames.delete(id),
  });
  let dock,
    captured = null,
    rendered;
  const focusCalls = [];
  const button = {
    offsetWidth: 76,
    offsetHeight: 82,
    focus: (options) => focusCalls.push(options),
    getBoundingClientRect: () =>
      rendered ?? {
        left: Number.parseFloat(dock.style.value.left) || width - 88,
        top: Number.parseFloat(dock.style.value.top) || height - 94,
      },
    setPointerCapture: (id) => {
      captured = id;
    },
    hasPointerCapture: (id) => captured === id,
    releasePointerCapture: () => {
      captured = null;
    },
  };
  const app = renderer.createApp({
    setup() {
      dock = useEdgeDock(shallowRef(button));
      return () => null;
    },
  });
  app.mount({});
  dispose = () => app.unmount();
  const point = (x, y, extra = {}) => ({
    pointerId: 1,
    isPrimary: true,
    button: 0,
    clientX: x,
    clientY: y,
    preventDefault() {},
    ...extra,
  });
  const flushResize = () => {
    for (const [id, fn] of frames) {
      frames.delete(id);
      fn();
    }
  };
  flushResize();
  return {
    dock,
    saved,
    win,
    point,
    button,
    focusCalls,
    captured: () => captured,
    flushResize,
    rendered: (value) => {
      rendered = value;
    },
  };
}

test('accepted primary pointer blocks native drag while retaining focus and ordinary clicks', () => {
  const f = fixture();
  let prevented = false;
  f.dock.down(
    f.point(340, 791, {
      preventDefault() {
        prevented = true;
      },
    }),
  );
  assert.equal(prevented, true);
  assert.equal(f.captured(), 1);
  assert.deepEqual(f.focusCalls, [{ preventScroll: true }]);
  f.dock.up(f.point(340, 791));
  assert.equal(f.dock.click({}), true, 'an ordinary tap still opens the chat');
});

test('non-primary, right-button and concurrent pointers keep their default behavior', () => {
  const f = fixture();
  let prevented = 0;
  const preventDefault = () => prevented++;
  f.dock.down(f.point(340, 791, { isPrimary: false, preventDefault }));
  f.dock.down(f.point(340, 791, { button: 2, preventDefault }));
  assert.equal(prevented, 0);
  assert.deepEqual(f.focusCalls, []);
  assert.equal(f.captured(), null);
  f.dock.down(f.point(340, 791));
  f.dock.down(f.point(340, 791, { pointerId: 2, preventDefault }));
  assert.equal(prevented, 0);
  assert.equal(f.focusCalls.length, 1);
  assert.equal(f.captured(), 1);
});

for (const width of [390, 1280]) {
  test(`held pointer moves through the centre, releases to the nearest side (${width}px)`, () => {
    const f = fixture({ width });
    const start = f.button.getBoundingClientRect();
    f.dock.down(f.point(start.left + 38, start.top + 41));
    f.dock.move(f.point(width / 2 - 25, 300));
    assert.equal(f.dock.style.value.left, width / 2 - 63 + 'px');
    assert.equal(f.dock.style.value.top, '259px');
    assert.equal(f.dock.dragging.value, true);
    assert.equal(f.dock.snapping.value, false);
    assert.equal(f.saved.size, 0, 'do not save an unfinished drag');
    f.win.dispatchEvent(new Event('resize'));
    f.flushResize();
    assert.equal(
      f.dock.style.value.left,
      width / 2 - 63 + 'px',
      'resize does not dock a held pointer',
    );
    f.dock.up(f.point(width / 2 - 25, 300));
    assert.equal(f.dock.dragging.value, false);
    assert.equal(f.dock.snapping.value, true);
    assert.equal(f.dock.style.value.left, '12px');
    assert.equal(f.dock.style.value.top, '259px');
    assert.equal(f.dock.position.value.edge, 'left');
  });
}

test('release uses the latest pointer position and clamps movement to the viewport', () => {
  const f = fixture();
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(-100, -100));
  assert.equal(f.dock.style.value.left, '12px');
  assert.equal(f.dock.style.value.top, '12px');
  f.dock.up(f.point(500, 1000));
  assert.equal(f.dock.position.value.edge, 'right');
  assert.equal(f.dock.style.value.left, '302px');
  assert.equal(f.dock.style.value.top, '750px');
});

test('drag does not open chat; the next ordinary tap can open it', () => {
  const f = fixture();
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(100, 300));
  f.dock.up(f.point(100, 300));
  let prevented = false,
    stopped = false;
  assert.equal(
    f.dock.click({
      preventDefault() {
        prevented = true;
      },
      stopPropagation() {
        stopped = true;
      },
    }),
    false,
  );
  assert.equal(prevented && stopped, true);
  f.dock.down(f.point(50, 300));
  f.dock.up(f.point(52, 302));
  assert.equal(f.dock.click({}), true);
});

test('cancel restores the prior dock and does not persist an incomplete gesture', () => {
  const f = fixture();
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(100, 300));
  f.dock.cancel();
  assert.equal(f.dock.style.value.left, '302px');
  assert.equal(f.dock.style.value.top, '750px');
  assert.equal(f.saved.size, 0);
  assert.equal(f.dock.dragging.value, false);
});

test('grabbing midway through snap freezes the visible position, then resumes from the finger', () => {
  const f = fixture();
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(150, 300));
  f.dock.up(f.point(150, 300));
  assert.equal(f.dock.snapping.value, true);
  f.rendered({ left: 60, top: 259 });
  f.dock.down(f.point(98, 300));
  assert.equal(f.dock.style.value.left, '60px');
  assert.equal(f.dock.snapping.value, false);
  f.dock.move(f.point(118, 320));
  assert.equal(f.dock.style.value.left, '80px');
  assert.equal(f.dock.style.value.top, '279px');
  f.dock.up(f.point(118, 320));
  assert.equal(f.dock.style.value.left, '12px');
});

test('final edge and height survive remount and resize', () => {
  const saved = new Map();
  let f = fixture({ saved });
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(100, 300));
  f.dock.up(f.point(100, 300));
  const ratio = f.dock.position.value.ratio;
  dispose();
  f = fixture({ saved, width: 360, height: 640 });
  assert.equal(f.dock.position.value.edge, 'left');
  assert.equal(f.dock.style.value.left, '12px');
  assert.equal(
    Number.parseFloat(f.dock.style.value.top),
    12 + (546 - 12) * ratio,
  );
  assert.deepEqual(
    Object.keys(JSON.parse(saved.values().next().value)).sort(),
    ['edge', 'ratio'],
  );
});

test('touch capture lost through window blur restores the dock', () => {
  const f = fixture();
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(100, 300));
  f.win.dispatchEvent(new Event('blur'));
  assert.equal(f.dock.dragging.value, false);
  assert.equal(f.dock.style.value.left, '302px');
});

test('snap state finishes and keyboard positioning remains usable', async () => {
  const f = fixture();
  f.dock.keyboard({ key: 'ArrowLeft', preventDefault() {} });
  assert.equal(f.dock.style.value.left, '12px');
  assert.equal(f.dock.snapping.value, true);
  await new Promise((resolve) => setTimeout(resolve, 510));
  assert.equal(f.dock.snapping.value, false);
  f.dock.keyboard({ key: 'Home', preventDefault() {} });
  assert.equal(f.dock.style.value.left, '302px');
  assert.equal(f.dock.style.value.top, '750px');
});

test('unavailable local storage does not interrupt dragging', () => {
  const f = fixture();
  globalThis.localStorage.setItem = () => {
    throw new Error('Storage disabled');
  };
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(100, 300));
  assert.doesNotThrow(() => f.dock.up(f.point(100, 300)));
  assert.equal(f.dock.style.value.left, '12px');
});

test('page scrolling and nearby buttons never move the manually chosen dock', () => {
  const f = fixture();
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(100, 300));
  f.dock.up(f.point(100, 300));
  const chosen = { ...f.dock.style.value };
  const preference = [...f.saved.entries()];
  let obstacleReads = 0;
  globalThis.document.querySelectorAll = () => [
    {
      getClientRects: () => [{}],
      getBoundingClientRect: () => {
        obstacleReads++;
        return { left: 0, right: 390, top: 250, bottom: 400 };
      },
    },
  ];
  for (const scrollY of [300, 900, 2000, 400, 0]) {
    f.win.scrollY = scrollY;
    f.win.dispatchEvent(new Event('scroll'));
    f.win.visualViewport.dispatchEvent(new Event('scroll'));
    f.flushResize();
    assert.deepEqual(f.dock.style.value, chosen);
    assert.deepEqual([...f.saved.entries()], preference);
  }
  globalThis.document.querySelectorAll = () => [];
  f.win.dispatchEvent(new Event('resize'));
  f.flushResize();
  assert.deepEqual(f.dock.style.value, chosen);
  assert.equal(
    obstacleReads,
    0,
    'nearby page content must not drive positioning',
  );
});

test('release keeps the chosen height even if a card or back-to-top button occupies it', () => {
  const f = fixture();
  globalThis.document.querySelectorAll = () => [
    {
      getClientRects: () => [{}],
      getBoundingClientRect: () => ({
        left: 300,
        right: 390,
        top: 250,
        bottom: 400,
      }),
    },
  ];
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(340, 300));
  f.dock.up(f.point(340, 300));
  assert.equal(f.dock.position.value.edge, 'right');
  assert.equal(f.dock.style.value.left, '302px');
  assert.equal(f.dock.style.value.top, '259px');
  const saved = JSON.parse(f.saved.values().next().value);
  assert.equal(saved.ratio, (259 - 12) / (750 - 12));
});

test('a smaller keyboard viewport constrains placement temporarily and restores the saved height', () => {
  const f = fixture();
  f.dock.down(f.point(340, 791));
  f.dock.move(f.point(100, 650));
  f.dock.up(f.point(100, 650));
  const chosen = { ...f.dock.style.value };
  const preference = [...f.saved.entries()];
  Object.assign(f.win.visualViewport, { height: 340, offsetTop: 40 });
  f.win.visualViewport.dispatchEvent(new Event('resize'));
  f.flushResize();
  const top = Number.parseFloat(f.dock.style.value.top);
  assert.ok(top >= 52 && top + 82 <= 368);
  assert.deepEqual([...f.saved.entries()], preference);
  Object.assign(f.win.visualViewport, { height: 844, offsetTop: 0 });
  f.win.visualViewport.dispatchEvent(new Event('resize'));
  f.flushResize();
  assert.deepEqual(f.dock.style.value, chosen);
  assert.deepEqual([...f.saved.entries()], preference);
});

test('pinch viewport offsets use CSS pixels and keep the selected side without saving', () => {
  const f = fixture();
  Object.assign(f.win.visualViewport, {
    width: 195,
    height: 422,
    offsetLeft: 120,
    offsetTop: 60,
    scale: 2,
  });
  f.win.visualViewport.dispatchEvent(new Event('resize'));
  f.flushResize();
  assert.equal(f.dock.style.value.left, '233px');
  assert.equal(f.dock.style.value.top, '394px');
  assert.equal(f.saved.size, 0);
  f.dock.keyboard({ key: 'ArrowLeft', preventDefault() {} });
  assert.equal(f.dock.style.value.left, '126px');
  const preference = [...f.saved.entries()];
  Object.assign(f.win.visualViewport, { offsetLeft: 60, offsetTop: 160 });
  f.win.visualViewport.dispatchEvent(new Event('scroll'));
  f.flushResize();
  assert.equal(f.dock.style.value.left, '66px');
  assert.equal(f.dock.style.value.top, '494px');
  assert.equal(
    f.dock.snapping.value,
    false,
    'do not animate through old offscreen positions',
  );
  assert.deepEqual([...f.saved.entries()], preference);
});

test('viewport resizing cancels an in-progress snap while preserving the dock preference', () => {
  const f = fixture();
  f.dock.keyboard({ key: 'ArrowLeft', preventDefault() {} });
  assert.equal(f.dock.snapping.value, true);
  Object.assign(f.win.visualViewport, { width: 320, height: 390 });
  f.win.dispatchEvent(new Event('resize'));
  f.flushResize();
  assert.equal(f.dock.snapping.value, false);
  assert.equal(f.dock.style.value.left, '12px');
  assert.equal(f.dock.style.value.top, '296px');
});

test('transient zero viewport dimensions and overscroll offsets keep finite visible coordinates', () => {
  const f = fixture();
  Object.assign(f.win.visualViewport, {
    width: 0,
    height: 0,
    offsetLeft: -40,
    offsetTop: -60,
  });
  f.win.visualViewport.dispatchEvent(new Event('resize'));
  f.flushResize();
  assert.equal(f.dock.style.value.left, '302px');
  assert.equal(f.dock.style.value.top, '750px');
});
