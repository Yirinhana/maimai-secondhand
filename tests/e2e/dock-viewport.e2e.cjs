const { test, expect } = require('@playwright/test');

// Fixture APIs only. Chromium viewport/device emulation and explicit visual-
// viewport mocks are regression checks, not physical WeChat/Safari acceptance.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('maimai-welcome-seen-v1', '1');
    // Each test has its own context; keep the saved position on reload.
  });
  await page.route('**/*', (route) => {
    const url = new URL(route.request().url());
    return url.hostname === '127.0.0.1' ? route.continue() : route.abort();
  });
  await page.route('**/api/v1/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname.replace(
      '/api/v1',
      '',
    );
    const products = Array.from({ length: 24 }, (_, index) => ({
      id: index + 1,
      title: `日常二手物品 ${index + 1}`,
      condition: 'GOOD',
      region: '上海市',
      priceCents: 5500,
      freightCents: 800,
      deliveryMethods: ['EXPRESS', 'MEETUP'],
      stockAvailable: 1,
      sellerId: 2,
      sellerNickname: '体验卖家',
      coverImage: null,
      status: 'ON_SALE',
    }));
    if (pathname === '/auth/csrf') return route.fulfill({ status: 204 });
    const responses = {
      '/auth/me': {
        id: 1,
        nickname: '视口测试用户',
        email: 'dock@example.invalid',
        roles: ['USER'],
        sellerStatus: 'NONE',
      },
      '/products': {
        content: products,
        totalElements: products.length,
        totalPages: 1,
        page: 0,
        size: 24,
      },
      '/categories': [{ id: 1, name: '生活家居', children: [] }],
      '/support/assistant': {
        name: '麦仔',
        enabled: false,
        maxMessageChars: 1000,
      },
      '/support/faq': [],
      '/support/chat': [],
      '/me/searches': [],
    };
    return route.fulfill({ json: responses[pathname] ?? [] });
  });
});

for (const width of [320, 390, 1920]) {
  test(`cards and page actions scroll behind the user's fixed dock (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/search');
    await expect(page.locator('.mm-product-card')).toHaveCount(24);
    const launcher = page.locator('.tina-launcher');
    await expect(launcher).toBeVisible();
    await launcher.focus();
    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('ArrowUp');
    await expect(launcher).not.toHaveClass(/is-snapping/);
    const before = await dockState(page);
    const preference = await page.evaluate(() => localStorage.getItem('maimai-maizai-dock-v1'));
    for (const top of [800, 1400, 400, 0]) {
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), top);
      await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      const after = await dockState(page);
      expect(after.x).toBeCloseTo(before.x, 1);
      expect(after.y).toBeCloseTo(before.y, 1);
      expect(after.within).toBe(true);
    }
    // A transformed application shell and a page button must not contain or
    // reposition the overlay. The button beneath it stays beneath it.
    await page.evaluate(() => {
      document.querySelector('#app').style.transform = 'translateZ(0)';
      const dock = document.querySelector('.tina-launcher').getBoundingClientRect();
      const button = document.createElement('button');
      button.textContent = '页面内容按钮';
      button.id = 'dock-page-obstacle';
      button.style.cssText = `position:fixed;z-index:50;left:${dock.left}px;top:${dock.top}px;width:${dock.width}px;height:${dock.height}px`;
      document.querySelector('main').append(button);
    });
    await expect.poll(async () => (await dockState(page)).y).toBeCloseTo(before.y, 1);
    expect(await launcher.evaluate((e) => {
      const r = e.getBoundingClientRect();
      return e.parentElement === document.body && e.contains(document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2));
    })).toBe(true);
    expect(await page.evaluate(() => localStorage.getItem('maimai-maizai-dock-v1'))).toBe(preference);
    await launcher.click();
    await expect(page.locator('#tina-dialog')).toBeVisible();
    await page.getByRole('button', { name: '关闭麦仔客服', exact: true }).click();
    expect((await dockState(page)).y).toBeCloseTo(before.y, 1);
    await page.screenshot({ path: test.info().outputPath(`manual-dock-${width}.png`) });
  });
}

test('manual drag snaps to the nearest edge and survives navigation and reload', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto('/search');
  const launcher = page.locator('.tina-launcher');
  await expect(launcher).toBeVisible();
  await expect(page.locator('.mm-product-card')).toHaveCount(24);
  await page.evaluate(() => {
    window.dockNativeDrags = 0;
    document.addEventListener('dragstart', () => window.dockNativeDrags++);
  });
  const start = await launcher.boundingBox();
  await page.mouse.move(start.x + start.width / 2, start.y + start.height / 2);
  await page.mouse.down();
  await page.mouse.move(250, 340, { steps: 12 });
  await expect(launcher).toHaveClass(/is-dragging/);
  expect(await page.evaluate(() => window.dockNativeDrags)).toBe(0);
  expect((await dockState(page)).x).toBeGreaterThan(100);
  await page.mouse.up();
  await expect(launcher).not.toHaveClass(/is-snapping|is-dragging/);
  const chosen = await dockState(page);
  expect(chosen.x).toBeCloseTo(12, 1);
  await expect(page.locator('#tina-dialog')).not.toBeVisible();
  const saved = await page.evaluate(() => localStorage.getItem('maimai-maizai-dock-v1'));
  expect(JSON.parse(saved).edge).toBe('left');
  await page.goto('/');
  await expect.poll(async () => (await dockState(page)).y).toBeCloseTo(chosen.y, 1);
  await page.reload();
  await expect.poll(async () => (await dockState(page)).y).toBeCloseTo(chosen.y, 1);
  expect(await page.evaluate(() => localStorage.getItem('maimai-maizai-dock-v1'))).toBe(saved);
});

test('touch drag floats under the finger and only snaps after release', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/search');
  const launcher = page.locator('.tina-launcher');
  await expect(launcher).toBeVisible();
  const r = await launcher.boundingBox();
  const cdp = await page.context().newCDPSession(page);
  try {
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: r.x + r.width / 2, y: r.y + r.height / 2 }] });
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: 145, y: 300 }] });
    await expect(launcher).toHaveClass(/is-dragging/);
    const floating = await dockState(page);
    expect(floating.x).toBeGreaterThan(40);
    await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    await expect(launcher).not.toHaveClass(/is-dragging|is-snapping/);
    const settled = await dockState(page);
    expect(settled.x).toBeCloseTo(12, 1);
    expect(settled.y).toBeCloseTo(floating.y, 1);
    await expect(page.locator('#tina-dialog')).not.toBeVisible();
  } finally { await cdp.detach(); }
});

async function dockState(page) {
  return page.locator('.tina-launcher').evaluate((element) => {
    const r = element.getBoundingClientRect();
    const visual = window.visualViewport;
    const viewport = {
      left: visual?.offsetLeft ?? 0,
      top: visual?.offsetTop ?? 0,
      width: visual?.width ?? innerWidth,
      height: visual?.height ?? innerHeight,
    };
    const gap = Math.min(
      Math.abs(r.left - viewport.left),
      Math.abs(viewport.left + viewport.width - r.right),
    );
    const css = getComputedStyle(element);
    const back = document
      .querySelector('.mm-back-top')
      ?.getBoundingClientRect();
    return {
      visible: css.visibility === 'visible' && css.display !== 'none',
      within:
        r.left >= viewport.left - 1 &&
        r.top >= viewport.top - 1 &&
        r.right <= viewport.left + viewport.width + 1 &&
        r.bottom <= viewport.top + viewport.height + 1,
      gap,
      screenGap: gap * (visual?.scale || 1),
      overlapTop:
        !!back &&
        r.left < back.right &&
        r.right > back.left &&
        r.top < back.bottom &&
        r.bottom > back.top,
      x: r.left,
      y: r.top,
      width: r.width,
      height: r.height,
      viewport,
    };
  });
}

for (const width of [320, 390]) {
  test(`back to top retains the mobile dock without focusing search (${width}px)`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 844 });
    // Exercise the actual smooth scroll branch as well as reduced motion.
    await page.emulateMedia({
      reducedMotion: width === 390 ? 'no-preference' : 'reduce',
    });
    await page.goto('/search');
    await expect(page.locator('.mm-product-card')).toHaveCount(24);
    const launcher = page.getByRole('button', {
      name: '打开麦仔客服',
      exact: true,
    });
    await expect(launcher).toBeVisible();
    await page.evaluate(() =>
      window.scrollTo({ top: 1100, behavior: 'instant' }),
    );
    await expect(page.getByRole('button', { name: '回到顶部' })).toBeVisible();
    await expect
      .poll(async () => (await dockState(page)).overlapTop)
      .toBe(false);
    await page.getByRole('button', { name: '回到顶部' }).click();
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
    await expect(page.locator('#main-content')).toBeFocused();
    await expect(launcher).toBeVisible();
    await expect.poll(async () => (await dockState(page)).within).toBe(true);
    await page.screenshot({
      path: test.info().outputPath(`back-top-${width}.png`),
    });
    await expect
      .poll(async () => (await dockState(page)).screenGap)
      .toBeLessThanOrEqual(13);
    await launcher.click();
    await expect(page.locator('#tina-dialog')).toBeVisible();
    await page
      .getByRole('button', { name: '关闭麦仔客服', exact: true })
      .click();
    await expect(launcher).toBeVisible();
  });
}

test('Chromium pinch scaling keeps the dock at the visible edge while scrolling', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/search');
  await expect(page.locator('.mm-product-card')).toHaveCount(24);
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Emulation.setPageScaleFactor', { pageScaleFactor: 2 });
  try {
    await expect.poll(() => page.evaluate(() => visualViewport.scale)).toBe(2);
    await expect.poll(async () => (await dockState(page)).within).toBe(true);
    await expect
      .poll(async () => (await dockState(page)).screenGap)
      .toBeLessThanOrEqual(13);
    await page.evaluate(() =>
      window.scrollTo({ top: 1300, behavior: 'instant' }),
    );
    await expect.poll(async () => (await dockState(page)).within).toBe(true);
    await expect
      .poll(async () => (await dockState(page)).gap)
      .toBeLessThanOrEqual(13);
    await expect(page.locator('.tina-launcher')).toBeVisible();
    await page.screenshot({
      path: test.info().outputPath('pinch-scale-2.png'),
    });
  } finally {
    await cdp.send('Emulation.setPageScaleFactor', { pageScaleFactor: 1 });
    await cdp.detach();
  }
  await expect.poll(async () => (await dockState(page)).within).toBe(true);
});

test('mocked visual viewport panning interrupts snap and preserves the chosen edge', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/search');
  const launcher = page.getByRole('button', {
    name: '打开麦仔客服',
    exact: true,
  });
  await expect(launcher).toBeVisible();
  await launcher.focus();
  await page.keyboard.press('ArrowLeft');
  const preference = await page.evaluate(() =>
    localStorage.getItem('maimai-maizai-dock-v1'),
  );
  await page.evaluate(() => {
    const visual = new EventTarget();
    Object.assign(visual, {
      width: 195,
      height: 422,
      offsetLeft: 120,
      offsetTop: 60,
      scale: 2,
    });
    // The mounted listener belongs to the native object: override native getters
    // rather than replacing the object, then dispatch its public viewport event.
    for (const key of ['width', 'height', 'offsetLeft', 'offsetTop', 'scale'])
      Object.defineProperty(window.visualViewport, key, {
        configurable: true,
        value: visual[key],
      });
    window.visualViewport.dispatchEvent(new Event('resize'));
    window.visualViewport.dispatchEvent(new Event('scroll'));
  });
  await expect(launcher).not.toHaveClass(/is-snapping/);
  await expect.poll(async () => (await dockState(page)).within).toBe(true);
  await expect.poll(async () => (await dockState(page)).x).toBe(126);
  expect(
    await page.evaluate(() => localStorage.getItem('maimai-maizai-dock-v1')),
  ).toBe(preference);
});
