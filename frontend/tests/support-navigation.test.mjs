import assert from "node:assert/strict";
import { test } from "node:test";
import { readFile } from "node:fs/promises";
import ts from "typescript";
async function moduleAt(path) {
  const code = ts.transpileModule(
    await readFile(new URL(path, import.meta.url), "utf8"),
    {
      compilerOptions: {
        target: ts.ScriptTarget.ES2022,
        module: ts.ModuleKind.ESNext,
      },
    },
  ).outputText;
  return import(
    "data:text/javascript;base64," + Buffer.from(code).toString("base64")
  );
}
const { safeSupportPath } = await moduleAt(
  "../src/modules/support/navigationResources.ts",
);
const { readCartSelection, saveCartSelection } = await moduleAt(
  "../src/modules/trade/cartSelection.ts",
);
const { orderNextStep } = await moduleAt(
  "../src/modules/trade/orderNextStep.ts",
);
test("support links accept only known internal routes", () => {
  for (const path of [
    "/policies",
    "/products/42",
    "/search?keyword=%E5%A4%96%E5%A5%97",
    "/orders",
  ])
    assert.equal(safeSupportPath(path), true);
  for (const path of [
    "https://evil.invalid",
    "//evil.invalid",
    "/\\evil.invalid",
    "/admin/users",
    "javascript:alert(1)",
    "/products/../admin",
    "/products/1\n",
  ])
    assert.equal(safeSupportPath(path), false);
});
test("cart selections are owner scoped and expire rather than silently reselecting", () => {
  const values = new Map(),
    storage = {
      getItem: (key) => values.get(key),
      setItem: (key, value) => values.set(key, value),
    };
  saveCartSelection(storage, 1, [2, 3], 100);
  assert.deepEqual(readCartSelection(storage, 1, 101), [2, 3]);
  assert.deepEqual(readCartSelection(storage, 2, 101), []);
  assert.deepEqual(readCartSelection(storage, 1, 86400101), []);
  assert.deepEqual(readCartSelection(storage, 1, 99), []);
  values.set("maimai:cart-selection:1", "{broken");
  assert.deepEqual(readCartSelection(storage, 1, 101), []);
});
test("storage failure does not break cart operations", () => {
  const denied = {
    getItem() {
      throw Error("Denied");
    },
    setItem() {
      throw Error("Denied");
    },
  };
  assert.deepEqual(readCartSelection(denied, 1), []);
  assert.doesNotThrow(() => saveCartSelection(denied, 1, [2]));
});
test("next action follows buyer or seller role and refunded order takes precedence", () => {
  const order = { fulfillmentStatus: "PENDING_PAYMENT", refundStatus: "NONE" };
  assert.match(orderNextStep(order, "buyer"), /收银台/);
  assert.match(orderNextStep(order, "seller"), /暂勿发货/);
  assert.match(
    orderNextStep({ ...order, fulfillmentStatus: "SHIPPED" }, "buyer"),
    /拿到商品后/,
  );
  assert.match(
    orderNextStep({ ...order, refundStatus: "FULL" }, "buyer"),
    /退款记录/,
  );
});
