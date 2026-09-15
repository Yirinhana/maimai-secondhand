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
  const button = {
    offsetWidth: 76,
    offsetHeight: 82,
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
    flushResize,
    rendered: (value) => {
      rendered = value;
    },
  };
}

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
