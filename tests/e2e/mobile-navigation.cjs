// Expanded navigation, introduction scenes and manual-address fallback.
// Uses local sessions created by mobile-compatibility.cjs; no external services.
const { chromium, expect } = require('@playwright/test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const OUT = path.resolve(__dirname, '../../.local/screenshots/058');
(async () => {
  const browser = await chromium.launch({
    channel: 'chrome',
    headless: true,
  });
  const evidence = [];
  try {
    for (const role of ['buyer', 'seller', 'admin']) {
      const c = await browser.newContext({
        baseURL: 'http://127.0.0.1:5173',
        storageState: path.join(OUT, role + '-state.json'),
        isMobile: true,
        hasTouch: true,
        viewport: { width: 390, height: 844 },
      });
      await c.addInitScript(() =>
        sessionStorage.setItem('maimai-welcome-seen-v1', '1'),
      );
      const p = await c.newPage();
      await p.goto(role === 'admin' ? '/admin' : '/');
      await p.waitForLoadState('networkidle');
      for (const [width, height] of [
        [320, 740],
        [390, 844],
        [844, 390],
      ]) {
        await p.setViewportSize({ width, height });
        await p.evaluate(() => scrollTo(0, 0));
        const cart = await p.locator('.mm-header__cart').boundingBox();
        const profile = await p.locator('.mm-user__trigger').boundingBox();
        assert.ok(
          cart.x + cart.width <= profile.x + 1,
          role + ' header controls overlap',
        );
        await p.getByRole('button', { name: '打开个人菜单' }).click();
        const menu = await p.locator('#mm-user-panel').boundingBox();
        assert.ok(
          menu.x >= 0 &&
            menu.x + menu.width <= width &&
            menu.y >= 0 &&
            menu.y + menu.height <= height,
          role + ' account menu clipped',
        );
        await p
          .locator('#mm-user-panel')
          .getByRole('menuitem', { name: '切换账号' })
          .scrollIntoViewIfNeeded();
        await expect(
          p
            .locator('#mm-user-panel')
            .getByRole('menuitem', { name: '切换账号' }),
        ).toBeInViewport();
        await p.screenshot({
          path: path.join(OUT, `menu-${role}-${width}.png`),
        });
        await p.keyboard.press('Escape');
        if (width < 760 || role === 'admin') {
          const trigger =
            role === 'admin'
              ? p.getByRole('button', { name: '展开工作导航' })
              : p.getByRole('button', { name: '打开导航菜单' });
          await trigger.click();
          const nav =
            role === 'admin'
              ? p.locator('#admin-menu')
              : p.locator('#mm-main-nav');
          await expect(nav).toBeVisible();
          const box = await nav.boundingBox();
          assert.ok(
            box.x >= 0 && box.x + box.width <= width + 1,
            'Navigation overflows',
          );
          await p.screenshot({
            path: path.join(OUT, `nav-${role}-${width}.png`),
          });
          if (role === 'admin')
            await p.getByRole('button', { name: '收起工作导航' }).click();
          else await trigger.click();
        }
        evidence.push({
          role,
          width,
          menu: 'usable',
          header: 'no overlap',
        });
      }
      await c.close();
    }
    const c = await browser.newContext({
      baseURL: 'http://127.0.0.1:5173',
      hasTouch: true,
      isMobile: true,
      viewport: { width: 390, height: 844 },
    });
    const p = await c.newPage();
    for (const [width, height] of [
      [320, 740],
      [390, 844],
      [844, 390],
    ]) {
      await p.setViewportSize({ width, height });
      await p.goto('/welcome?mobile-width=' + width);
      const region = p.getByRole('region', { name: '麦麦介绍' });
      for (let scene = 0; scene < 4; scene++) {
        await expect(p.locator('.welcome-scene-count')).toContainText(
          '0' + (scene + 1),
        );
        await expect(region).toHaveAttribute('aria-busy', 'false');
        // Observe real animation completion; screenshots should not freeze the entrance blur.
        await p.evaluate(async () => {
          await Promise.all(
            document
              .getAnimations()
              .filter(
                (a) =>
                  a.effect?.getComputedTiming().iterations !== Infinity,
              )
              .map((a) => a.finished.catch(() => {})),
          );
        });
        const bounds = await p.evaluate(() => ({
          width: innerWidth,
          root: document.documentElement.scrollWidth,
          body: document.body.scrollWidth,
          viewport: innerHeight,
          page: document
            .querySelector('.welcome-page')
            .getBoundingClientRect().height,
        }));
        assert.ok(
          bounds.root <= width &&
            bounds.body <= width &&
            bounds.page <= height + 1,
          'Welcome scene leaves viewport',
        );
        await p.screenshot({
          path: path.join(OUT, `welcome-${scene + 1}-${width}.png`),
        });
        evidence.push({ scene: scene + 1, width, overflow: false });
        if (scene < 3) {
          await region.focus();
          await p.keyboard.press('ArrowDown');
        }
      }
    }
    await c.close();
    const ac = await browser.newContext({
      baseURL: 'http://127.0.0.1:5173',
      hasTouch: true,
      isMobile: true,
      storageState: path.join(OUT, 'buyer-state.json'),
    });
    const ap = await ac.newPage();
    await ap.route('**/api/v1/maps/js-config', (r) =>
      r.fulfill({
        json: { enabled: false, serviceHost: '/_AMapService' },
      }),
    );
    await ap.goto('/me?tab=addresses');
    await ap.waitForLoadState('networkidle');
    await ap
      .getByRole('button', { name: '新增地址', exact: true })
      .click();
    await ap
      .getByRole('button', { name: '使用地图辅助选址', exact: true })
      .click();
    await expect(ap.getByRole('alert')).toContainText('仍可手动填写地址');
    for (const [width, height] of [
      [320, 740],
      [390, 844],
      [844, 390],
    ]) {
      await ap.setViewportSize({ width, height });
      assert.ok(
        await ap.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth,
        ),
        'Map fallback overflows',
      );
      await ap
        .getByLabel('详细地址', { exact: true })
        .fill('手动填写仍然可用，不保存');
      await ap.screenshot({
        path: path.join(OUT, `map-fallback-${width}.png`),
      });
      evidence.push({ width, mapFallback: 'manual address usable' });
    }
    fs.writeFileSync(
      path.join(OUT, 'navigation.json'),
      JSON.stringify(evidence, null, 2),
    );
    console.log(
      'PASS',
      evidence.length,
      'navigation, scene and map fallback checks',
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
