const { test, expect } = require('@playwright/test');

// Fixture APIs only. Chromium viewport/device emulation and explicit visual-
// viewport mocks are regression checks, not physical WeChat/Safari acceptance.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('maimai-welcome-seen-v1', '1');
    localStorage.removeItem('maimai-maizai-dock-v1');
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
