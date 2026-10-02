// Explicitly authorized sample posts via existing accounts and normal moderation APIs.
const { chromium } = require('@playwright/test');
const fs = require('node:fs'),
  path = require('node:path');
const ROOT = path.resolve(__dirname, '../..'),
  BASE = process.env.MAIMAI_044_BASE || 'http://127.0.0.1:5173';
if (
  !['http://127.0.0.1:5173', process.env.MAIMAI_E2E_ALLOWED_ORIGIN].includes(BASE)
)
  throw Error('Unapproved host');
const privateData = JSON.parse(
  fs
    .readFileSync(
      path.join(ROOT, '.local/private/demo-accounts-040.json'),
      'utf8',
    )
    .replace(/^\uFEFF/, ''),
);
const posts = JSON.parse(
  fs.readFileSync(
    path.join(ROOT, 'tests/e2e/fixtures/community-content-v060.json'),
    'utf8',
  ),
);
const statePath = path.join(
  ROOT,
  '.local/private',
  `044-community-${BASE.startsWith('http:') ? 'local' : 'public'}.json`,
);
const state = fs.existsSync(statePath)
  ? JSON.parse(fs.readFileSync(statePath, 'utf8'))
  : { posts: [], comments: [] };
const save = () => fs.writeFileSync(statePath, JSON.stringify(state, null, 2));
async function request(ctx, method, route, data, attempt=0) {
  await ctx.request.get(BASE + '/api/v1/auth/csrf');
  const csrf = (await ctx.cookies(BASE)).find((c) => c.name === 'XSRF-TOKEN');
  const r = await ctx.request.fetch(BASE + '/api/v1' + route, {
    method,
    data,
    headers: { 'X-XSRF-TOKEN': decodeURIComponent(csrf?.value || '') },
  });
  if(r.status()===429 && attempt<3){
    console.log('Rate limit reached; waiting before resuming saved batch.');
    await new Promise(resolve=>setTimeout(resolve,32000));
    return request(ctx,method,route,data,attempt+1);
  }
  if (!r.ok()) throw Error(`${method} ${route} status=${r.status()}`);
  return r.status() === 204 ? null : r.json();
}
(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const sessions = new Map();
    let lastLogin = 0;
    async function session(user) {
      if (sessions.has(user.email)) return sessions.get(user.email);
      await new Promise((resolve) =>
        setTimeout(resolve, Math.max(0, 7500 - (Date.now() - lastLogin))),
      );
      lastLogin = Date.now();
      const ctx = await browser.newContext();
      await request(ctx, 'POST', '/auth/login', {
        email: user.email,
        password: privateData.password,
      });
      sessions.set(user.email, ctx);
      return ctx;
    }
    const admin = await session({ email: privateData.adminEmail });
    for (let i = 0; i < posts.length; i++) {
      const saved=state.posts.find(row=>row.index===i);
      if(saved?.replyId){
        const published=await request(admin,'GET',`/community/demands/${saved.id}/replies?size=50`);
        if(published.items.some(r=>r.id===saved.replyId && r.content===posts[i].reply))continue;
      }
      const p = posts[i],
        buyer = await session(privateData.buyers[i]),
        seller = await session(privateData.sellers[i]);
      const mine = await request(buyer, 'GET', '/community/demands/me?size=50');
      let d = mine.items.find((d) => d.title === p.title);
      if (!d) {
        d = await request(buyer, 'POST', '/community/demands', {
          title: p.title,
          description: p.description,
          budgetMinCents: p.min,
          budgetMaxCents: p.max,
          region: p.region,
        });
        state.posts.push({ id: d.id, index: i });
        save();
      }
      if (d.status === 'PENDING')
        await request(
          admin,
          'POST',
          `/community/admin/demands/${d.id}/review`,
          { approve: true, reason: '用户授权的社区实例，内容审核通过' },
        );
      const replies = await request(
        seller,
        'GET',
        '/community/replies/me?size=50',
      );
      let reply = replies.items.find(
        (r) => r.demandId === d.id && r.content === p.reply,
      );
      if (!reply) {
        reply = await request(
          seller,
          'POST',
          `/community/demands/${d.id}/replies`,
          { content: p.reply },
        );
        const saved = state.posts.find((x) => x.id === d.id);
        if (saved) saved.replyId = reply.id;
        save();
      }
      if (reply.status === 'PENDING')
        await request(
          admin,
          'POST',
          `/community/admin/demand-replies/${reply.id}/review`,
          { approve: true, reason: '用户授权的社区回复，内容审核通过' },
        );
    }
    console.log(
      JSON.stringify({
        status: 'COMMUNITY_READY',
        base: BASE,
        posts: 12,
        replies: 12,
      }),
    );
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error(
    'Community seed did not finish. ' +
      (/^(GET|POST) /.test(e.message) ? e.message : e.name),
  );
  process.exitCode = 1;
});
