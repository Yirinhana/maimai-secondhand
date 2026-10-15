const { test, expect } = require('@playwright/test');

// All business APIs are fixtures. No production account or password is used.
async function fixtures(page, admin = false) {
  await page.addInitScript(() =>
    sessionStorage.setItem('maimai-welcome-seen-v1', '1'),
  );
  await page.route('**/*', (route) => {
    const host = new URL(route.request().url()).hostname;
    return ['127.0.0.1', 'localhost'].includes(host)
      ? route.continue()
      : route.abort();
  });
  await page.route('**/api/v1/**', (route) => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/auth/csrf') return route.fulfill({ status: 204 });
    if (path === '/auth/me')
      return route.fulfill(
        admin
          ? {
              json: {
                id: 1,
                nickname: '导航测试管理员',
                email: 'navigation@example.invalid',
                roles: ['SUPER_ADMIN'],
                sellerStatus: 'NONE',
              },
            }
          : { status: 401, json: { code: 'UNAUTHENTICATED' } },
      );
    if (path === '/admin/users')
      return route.fulfill({ json: { content: [], total: 0, size: 15 } });
    if (path === '/support/assistant')
      return route.fulfill({ json: { name: '麦仔', enabled: false } });
    return route.fulfill({ json: [] });
  });
}

for (const width of [390, 1280]) {
  test(`admin navigation separates group headings and clickable rows at ${width}px`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await fixtures(page, true);
    await page.goto('/admin/users');
    const nav = page.getByRole('navigation', { name: '后台导航' });
    const toggle = page.getByRole('button', { name: '展开工作导航' });
    if (width < 900) await toggle.click();
    await expect(nav).toBeVisible();
    for (const name of [
      '概览与账号',
      '商品与卖家',
      '交易与服务',
      '内容与社区',
    ]) {
      const heading = nav.getByRole('heading', { name, exact: true });
      await expect(heading).toBeVisible();
      await expect(nav.getByRole('link', { name, exact: true })).toHaveCount(0);
      const styling = await heading.evaluate((e) => {
        const style = getComputedStyle(e);
        return {
          background: style.backgroundColor,
          border: style.borderBottomWidth,
        };
      });
      expect(styling.background).not.toBe('rgba(0, 0, 0, 0)');
      expect(parseFloat(styling.border)).toBeGreaterThan(0);
    }
    const users = nav.getByRole('link', { name: '用户管理', exact: true });
    await expect(users).toHaveAttribute('aria-current', 'page');
    const controls = await nav.getByRole('link').evaluateAll((links) =>
      links.map((link) => {
        const r = link.getBoundingClientRect();
        return {
          width: r.width,
          height: r.height,
          clipped: link.scrollWidth > link.clientWidth + 1,
        };
      }),
    );
    expect(controls).toHaveLength(14);
    expect(
      controls.every((r) => r.width >= 44 && r.height >= 44 && !r.clipped),
    ).toBe(true);
    await testInfo.attach('admin-navigation', {
      body: await page.locator('.mm-admin__navigation').screenshot({
        path: testInfo.outputPath('admin-navigation.png'),
      }),
      contentType: 'image/png',
    });
    if (width < 900) {
      await nav.getByRole('heading', { name: '概览与账号' }).click();
      await expect(nav).toBeVisible();
      await users.click();
      await expect(nav).toBeHidden();
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    }
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
  });

  test(`password reveal preserves typing, focus and autofill at ${width}px`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await fixtures(page);
    await page.goto('/login');
    const password = page.getByLabel('密码', { exact: true });
    const field = password.locator('..');
    const toggle = field.getByRole('button');
    await expect(password).toHaveAttribute('type', 'password');
    await expect(password).toHaveAttribute('autocomplete', 'current-password');
    await expect(toggle).toHaveCount(1);
    await expect(toggle).toHaveAccessibleName('显示密码');
    await expect(toggle).toHaveAttribute(
      'aria-controls',
      await password.getAttribute('id'),
    );
    await password.focus();
    await password.pressSequentially('Sample-only-123');
    await expect(password).toBeFocused();
    await expect(toggle).toBeVisible();
    await testInfo.attach('typed-password-native-reveal-check', {
      body: await field.screenshot({
        path: testInfo.outputPath('password-typed.png'),
      }),
      contentType: 'image/png',
    });
    await page.getByLabel('邮箱', { exact: true }).focus();
    await expect(toggle).toBeVisible();
    await password.focus();
    await toggle.click();
    await expect(password).toHaveAttribute('type', 'text');
    await expect(password).toHaveValue('Sample-only-123');
    await expect(password).toBeFocused();
    await expect(toggle).toHaveAccessibleName('隐藏密码');
    await expect(toggle).toHaveAttribute('aria-pressed', 'true');
    // Keyboard users can use the same action without submitting the form.
    await toggle.focus();
    await page.keyboard.press('Space');
    await expect(password).toHaveAttribute('type', 'password');
    await expect(password).toBeFocused();
    await expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await password.fill('');
    await expect(toggle).toBeVisible();
    await password.pressSequentially('Second-sample-456');
    await page.getByLabel('邮箱', { exact: true }).focus();
    await password.focus();
    await expect(password).toHaveValue('Second-sample-456');
    await expect(toggle).toHaveCount(1);
    await expect(toggle).toHaveAccessibleName('显示密码');
    await testInfo.attach('retyped-refocused-password-native-reveal-check', {
      body: await field.screenshot({
        path: testInfo.outputPath('password-retyped.png'),
      }),
      contentType: 'image/png',
    });
    await expect(page).toHaveURL(/\/login$/);
  });
}
