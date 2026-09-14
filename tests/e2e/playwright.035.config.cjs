const {defineConfig} = require('@playwright/test');
const path = require('path');
const run = process.env.MAIMAI_035_RUN || Date.now().toString();
process.env.MAIMAI_035_RUN = run;
const out = path.resolve(__dirname, '../../.local/screenshots');
module.exports = defineConfig({
  testDir: '.', testMatch: 'interface-renewal.e2e.cjs',
  timeout: 90000, expect: {timeout: 12000}, workers: 1, fullyParallel: false, retries: 0,
  reporter: [['list'], ['json', {outputFile: path.join(out, `035-${run}-results.json`)}]],
  outputDir: path.join(out, `035-${run}-artifacts`),
  use: {baseURL:'http://127.0.0.1:5173', channel:'chrome', headless:true,
    viewport:{width:1440,height:960},locale:'zh-CN',timezoneId:'Asia/Shanghai',
    actionTimeout:15000,trace:'off',video:'off',screenshot:'only-on-failure'},
});
