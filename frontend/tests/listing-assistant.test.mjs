import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
const source = await readFile(
  new URL('../src/modules/catalog/listingAssistant.ts', import.meta.url),
  'utf8',
);
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
  },
}).outputText;
const {
  categoryLeaves,
  categoryRoot,
  blankRecognitionPatch,
  askingPricePoints,
} = await import(
  'data:text/javascript;base64,' + Buffer.from(compiled).toString('base64')
);
const tree = [
  {
    id: 1,
    name: '数码',
    children: [
      { id: 11, name: '相机', children: [] },
      {
        id: 12,
        name: '电脑',
        children: [{ id: 121, name: '笔记本', children: [] }],
      },
    ],
  },
  { id: 2, name: '其他', children: [] },
];
const empty = () => ({ title: '', categoryId: '', description: '' });
const edits = () => ({ title: 0, categoryId: 0, description: 0 });
const suggestion = {
  title: '银色便携相机',
  categoryId: 11,
  description: '照片可见银色机身与黑色镜头。',
  visibleFeatures: [],
  checklist: [],
};
test('category selector exposes roots separately and only selectable leaves', () => {
  assert.deepEqual(categoryLeaves(tree), [
    { id: 11, name: '数码 / 相机' },
    { id: 121, name: '数码 / 电脑 / 笔记本' },
    { id: 2, name: '其他' },
  ]);
  assert.equal(categoryRoot(tree, '121'), '1');
  assert.equal(categoryRoot(tree, '2'), '2');
  assert.equal(categoryRoot(tree, '999'), '');
});
test('recognition fills blank fields but excludes non-draft attributes', () => {
  const result = blankRecognitionPatch(
    empty(),
    empty(),
    edits(),
    edits(),
    { ...suggestion, priceCents: 10, stock: 5 },
    [11],
  );
  assert.deepEqual(result.patch, {
    title: suggestion.title,
    categoryId: '11',
    description: suggestion.description,
  });
  assert.equal('priceCents' in result.patch, false);
  assert.equal('stock' in result.patch, false);
});
test('seller text present at request start is never overwritten', () => {
  const start = { ...empty(), title: '我的相机' };
  const result = blankRecognitionPatch(
    start,
    start,
    edits(),
    edits(),
    suggestion,
    [11],
  );
  assert.equal(result.patch.title, undefined);
  assert.deepEqual(result.kept, ['标题']);
});
test('typing and then clearing during recognition remains an intentional edit', () => {
  const changed = { ...edits(), title: 2, categoryId: 1 };
  const result = blankRecognitionPatch(
    empty(),
    empty(),
    changed,
    edits(),
    suggestion,
    [11],
  );
  assert.deepEqual(result.patch, { description: suggestion.description });
  assert.deepEqual(result.kept, ['标题', '分类']);
});
test('unknown and parent-only AI categories cannot be submitted as valid leaves', () => {
  for (const id of [1, 999, null]) {
    const result = blankRecognitionPatch(
      empty(),
      empty(),
      edits(),
      edits(),
      { ...suggestion, categoryId: id },
      [11, 121, 2],
    );
    assert.equal(result.patch.categoryId, undefined);
  }
});
test('price comparison sorts immutable asking-price samples and uses price units accurately', () => {
  const items = [
    { id: 3, priceCents: 20000 },
    { id: 1, priceCents: 10000 },
    { id: 2, priceCents: 10000 },
  ];
  const graph = askingPricePoints(items, 15000);
  assert.deepEqual(
    graph.sorted.map((item) => item.id),
    [1, 2, 3],
  );
  assert.deepEqual(
    items.map((item) => item.id),
    [3, 1, 2],
  );
  assert.equal(graph.points[0].y, graph.points[1].y);
  assert.ok(
    graph.points[2].y < graph.referenceY &&
      graph.referenceY < graph.points[0].y,
  );
});
test('empty and one-sample graphs do not fabricate samples or time series', () => {
  assert.equal(askingPricePoints([], null).points.length, 0);
  const graph = askingPricePoints(
    [{ id: 1, priceCents: 1800 }],
    null,
    360,
  );
  assert.equal(graph.points.length, 1);
  assert.equal(graph.points[0].x, (graph.left + graph.right) / 2);
  assert.equal(graph.referenceY, null);
});
test('invalid money does not produce SVG NaN or out-of-range reference values', () => {
  const graph = askingPricePoints(
    [
      { id: 1, priceCents: NaN },
      { id: 2, priceCents: -1 },
      { id: 3, priceCents: 1200 },
    ],
    Infinity,
  );
  assert.equal(graph.points.length, 1);
  assert.equal(graph.referenceY, null);
  assert.ok(
    graph.points.every(
      (point) => Number.isFinite(point.x) && Number.isFinite(point.y),
    ),
  );
});
