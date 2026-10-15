const { defineConfig } = require('@playwright/test');
const path = require('node:path');
module.exports = defineConfig({
  testDir: '.',
  testMatch: [
    'controls-interaction.e2e.cjs',
    'layout-hierarchy.e2e.cjs',
    'workspace-layout.e2e.cjs',
    'admin-password.e2e.cjs',
    'dock-viewport.e2e.cjs',
  ],
  workers: 1,
  retries: 0,
  timeout: 30000,
  expect: { timeout: 7000 },
  outputDir: '../../.local/controls-regression',
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:5192',
    channel: 'chrome',
    headless: true,
    locale: 'zh-CN',
    viewport: { width: 1280, height: 900 },
    reducedMotion: 'reduce',
  },
  webServer: {
    command:
      'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5192 --strictPort',
    cwd: path.resolve(__dirname, '../../frontend'),
    url: 'http://127.0.0.1:5192',
    reuseExistingServer: false,
  },
});
