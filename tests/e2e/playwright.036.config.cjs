const { defineConfig } = require('@playwright/test');
const path = require('path');
const run = process.env.MAIMAI_036_RUN || Date.now().toString();
process.env.MAIMAI_036_RUN = run;
const output = path.resolve(__dirname, '../../.local/screenshots');
module.exports = defineConfig({
  testDir: '.',
  testMatch: 'ux-polish.e2e.cjs',
  workers: 1,
  retries: 0,
  timeout: 90000,
  expect: { timeout: 12000 },
  reporter: [
    ['list'],
    ['json', { outputFile: path.join(output, `036-${run}-results.json`) }],
  ],
  outputDir: path.join(output, `036-${run}-artifacts`),
  use: {
    channel: 'chrome',
    headless: true,
    baseURL: 'http://127.0.0.1:5173',
    viewport: { width: 1440, height: 960 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
    actionTimeout: 15000,
  },
});
