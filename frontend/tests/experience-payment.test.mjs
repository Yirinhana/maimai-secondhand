import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(new URL('../src/modules/trade/experiencePayment.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText;
const { isExperienceProduct, experienceProductNotice, SELLER_SIMULATION_SOURCE } = await import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'));

test('experience payment accepts only the three exact server-approved product sources', () => {
  for (const value of ['maimai-experience-045', 'maimai-experience-catalog-021', SELLER_SIMULATION_SOURCE]) assert.equal(isExperienceProduct(value), true);
  for (const value of [null, undefined, '', 'arbitrary', 'maimai-experience-045-extra', ' maimai-experience-045', 'MAIMAI-EXPERIENCE-045', 'maimai-experience-checkout-v1']) assert.equal(isExperienceProduct(value), false);
});
test('seller-submitted simulation photos are never described as generated catalog images', () => {
  const message = experienceProductNotice(SELLER_SIMULATION_SOURCE);
  assert.match(message, /卖家提供/);
  assert.match(message, /不发生扣款或实际发货/);
  assert.doesNotMatch(message, /生成素材|生成图/);
  assert.match(experienceProductNotice('maimai-experience-catalog-021'), /生成素材/);
  assert.match(experienceProductNotice('maimai-experience-045'), /生成素材/);
  assert.match(experienceProductNotice('unknown'), /暂未开放结算/);
  assert.equal(experienceProductNotice(null), '');
});
