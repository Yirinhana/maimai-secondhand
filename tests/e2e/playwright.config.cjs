const { defineConfig } = require('@playwright/test');
const path = require('path');
const run = process.env.MAIMAI_E2E_RUN || Date.now().toString();
process.env.MAIMAI_E2E_RUN = run;
const output = path.resolve(__dirname, '../../.local/screenshots');

module.exports = defineConfig({
  testDir: '.',
  testMatch: '*.spec.cjs',
  timeout: 120000,
  expect: { timeout: 12000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['json', { outputFile: path.join(output, `022-${run}-results.json`) }],
  ],
  outputDir: path.join(output, `022-${run}-artifacts`),
  use: {
    baseURL: 'http://127.0.0.1:5173',
    channel: 'chrome',
    headless: true,
    viewport: { width: 1440, height: 960 },
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    // Real map URLs contain browser keys. Do not retain raw network traces.
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
    actionTimeout: 15000,
  },
});
