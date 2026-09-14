const { defineConfig } = require('@playwright/test');
const path = require('node:path');
const run = process.env.MAIMAI_039_RUN || Date.now().toString();
process.env.MAIMAI_039_RUN = run;
module.exports = defineConfig({
  testDir: '.',
  testMatch: 'tina-support.e2e.cjs',
  workers: 1,
  retries: 0,
  timeout: 45000,
  expect: { timeout: 10000 },
  reporter: [
    ['list'],
    [
      'json',
      {
        outputFile: path.resolve(
          __dirname,
          `../../.local/screenshots/039-${run}-results.json`,
        ),
      },
    ],
  ],
  outputDir: path.resolve(
    __dirname,
    `../../.local/screenshots/039-${run}-artifacts`,
  ),
  use: {
    channel: 'chrome',
    headless: true,
    baseURL: 'http://127.0.0.1:5173',
    viewport: { width: 1440, height: 960 },
    locale: 'zh-CN',
    trace: 'off',
    video: 'off',
    screenshot: 'only-on-failure',
  },
});
