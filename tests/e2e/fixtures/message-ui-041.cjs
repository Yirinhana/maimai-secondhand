// Controlled UI responses only. No chat messages, read receipts or block changes reach the server.
module.exports = async function messageFixture(page, me) {
  let blocked = false,
    read = false;
  const sent = [];
  const conversations = [
    {
      id: 904101,
      otherUserId: 904201,
      otherNickname: '林的小店 · 界面验收',
      productId: 904301,
      productTitle: 'Java 教材 · 界面样例',
      lastMessage: '书上有少量笔记，可以看细节图。',
      unread: 2,
    },
    {
      id: 904102,
      otherUserId: 904202,
      otherNickname: '阿青 · 界面验收',
      productId: 904302,
      productTitle: '运动耳机 · 界面样例',
      lastMessage: '好的，周六可以当面看。',
      unread: 0,
    },
  ].map((c) => ({
    ...c,
    otherAvatarUrl: null,
    updatedAt: '2026-09-14T10:00:00Z',
  }));
  const messages = [
    {
      id: 904501,
      conversationId: 904101,
      senderId: 904201,
      clientId: '00000000-0000-4000-8000-000000904501',
      body: '你好，这本教材还在。有少量笔记，介意的话可以先看图片。',
      attachmentUrl: null,
      createdAt: '2026-09-14T09:50:00Z',
    },
    {
      id: 904502,
      conversationId: 904101,
      senderId: me.id,
      clientId: '00000000-0000-4000-8000-000000904502',
      body: '谢谢，可以看看目录和笔记的情况吗？',
      attachmentUrl: null,
      createdAt: '2026-09-14T09:52:00Z',
    },
    {
      id: 904503,
      conversationId: 904101,
      senderId: 904201,
      clientId: '00000000-0000-4000-8000-000000904503',
      body: '可以。书页完整，封面有正常使用痕迹。',
      attachmentUrl: null,
      createdAt: '2026-09-14T09:53:00Z',
    },
  ];
  await page.routeWebSocket('**/api/v1/messages/socket', (ws) =>
    ws.send(JSON.stringify({ type: 'ready' })),
  );
  await page.route('**/api/v1/messages/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      pathname = url.pathname;
    if (pathname.endsWith('/overview'))
      return route.fulfill({
        json: {
          conversations: 2,
          unreadConversations: read ? 0 : 1,
          unreadMessages: read ? 0 : 2,
        },
      });
    if (pathname.endsWith('/summary'))
      return route.fulfill({ json: conversations[0] });
    if (pathname.endsWith('/block')) {
      if (req.method() === 'PUT') blocked = req.postDataJSON().blocked;
      return route.fulfill({
        json: { blockedByMe: blocked, blockedEitherDirection: blocked },
      });
    }
    if (pathname.endsWith('/read')) {
      read = true;
      return route.fulfill({ status: 204 });
    }
    if (pathname === '/api/v1/messages/conversations') {
      const keyword = url.searchParams.get('keyword') || '';
      let results = conversations.map((c, i) => ({
        ...c,
        unread: i === 0 && !read ? 2 : 0,
      }));
      if (keyword) results = keyword === 'Java' ? results.slice(0, 1) : [];
      if (url.searchParams.get('unreadOnly') === 'true')
        results = results.filter((c) => c.unread > 0);
      return route.fulfill({ json: results });
    }
    if (pathname === '/api/v1/messages/conversations/904101') {
      if (req.method() === 'POST') {
        const body = req.postDataJSON();
        sent.push(body);
        const item = {
          id: 904504 + sent.length,
          conversationId: 904101,
          senderId: me.id,
          clientId: body.clientId,
          body: body.body,
          attachmentUrl: null,
          createdAt: '2026-09-14T10:01:00Z',
        };
        messages.push(item);
        return route.fulfill({ json: item });
      }
      return route.fulfill({
        json: {
          items: [...messages].reverse(),
          hasMore: false,
          nextBeforeId: null,
        },
      });
    }
    throw new Error('Unexpected message UI fixture endpoint');
  });
  return {
    sent,
    get read() {
      return read;
    },
  };
};
