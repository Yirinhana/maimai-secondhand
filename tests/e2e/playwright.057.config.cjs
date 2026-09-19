const { defineConfig } = require('@playwright/test');
const path = require('path');
module.exports = defineConfig({
  testDir: '.',
  testMatch: 'experience-quality-v0140.e2e.cjs',
  workers: 1,
  retries: 0,
  timeout: 60000,
  expect: { timeout: 12000 },
  reporter: [
    ['list'],
    [
      'json',
      {
        outputFile: path.resolve(
          __dirname,
          '../../.local/screenshots/057/results.json',
        ),
      },
    ],
  ],
  outputDir: path.resolve(
    __dirname,
    '../../.local/screenshots/057/artifacts',
  ),
  use: {
    baseURL: 'http://127.0.0.1:5173',
    channel: 'chrome',
    headless: true,
    viewport: { width: 1440, height: 960 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
    actionTimeout: 12000,
  },
});
