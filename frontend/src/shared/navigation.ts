import { computed, onScopeDispose, readonly, shallowRef, watch } from 'vue';

type TradeSection = 'buyer' | 'seller' | 'admin';
interface DetailNavigation {
  owner: symbol;
  routeKey: string;
  userId: number;
  section: TradeSection;
}

const detailNavigation = shallowRef<DetailNavigation | null>(null);
export const activeDetailNavigation = readonly(detailNavigation);

/** Navigation only: resource permissions remain with the server. */
export function useTradeDetailNavigation<
  T extends { buyerId: number; sellerId: number },
>(options: {
  detail: () => T | null;
  user: () => { id: number; roles: readonly string[] } | null;
  routeKey: () => string;
  matchesRoute: (detail: T) => boolean;
}) {
  const owner = Symbol('trade-detail-navigation');
  const context = computed<DetailNavigation | null>(() => {
    const routeKey = options.routeKey();
    const detail = options.detail();
    const user = options.user();
    if (!detail || !user || !options.matchesRoute(detail)) return null;
    let section: TradeSection;
    if (detail.buyerId === user.id) section = 'buyer';
    else if (detail.sellerId === user.id) section = 'seller';
    else if (
      user.roles.some((role) =>
        ['SUPER_ADMIN', 'OPERATOR', 'SUPPORT'].includes(role),
      )
    )
      section = 'admin';
    else return null;
    return { owner, routeKey, userId: user.id, section };
  });
  const clear = () => {
    if (detailNavigation.value?.owner === owner) detailNavigation.value = null;
  };
  watch(
    context,
    (value) => {
      if (value) detailNavigation.value = value;
      else clear();
    },
    { immediate: true, flush: 'sync' },
  );
  onScopeDispose(clear);
  return computed(() => context.value?.section ?? null);
}

export const pageInfo: Record<string, { title: string; section: string }> = {
  home: { title: '逛逛闲置', section: 'market' },
  search: { title: '发现好物', section: 'market' },
  'product-detail': { title: '商品详情', section: 'market' },
  seller: { title: '卖家主页', section: 'market' },
  demands: { title: '求购广场', section: 'community' },
  'my-community': { title: '收藏与关注', section: 'account' },
  publish: { title: '发布闲置', section: 'seller' },
  'publish-edit': { title: '编辑商品', section: 'seller' },
  'my-products': { title: '我的商品', section: 'seller' },
  'seller-orders': { title: '我卖出的', section: 'seller' },
  'seller-bargains': { title: '收到的议价', section: 'seller' },
  'seller-aftersales': { title: '卖家售后', section: 'seller' },
  cart: { title: '购物车', section: 'buyer' },
  checkout: { title: '确认订单', section: 'buyer' },
  'my-orders': { title: '我买到的', section: 'buyer' },
  'order-detail': { title: '订单详情', section: 'transaction' },
  'my-bargains': { title: '我的议价', section: 'buyer' },
  'my-aftersales': { title: '我的售后', section: 'buyer' },
  'aftersale-detail': { title: '售后详情', section: 'transaction' },
  account: { title: '个人中心', section: 'account' },
  'account-closure': { title: '账号注销申请', section: 'account' },
  messages: { title: '消息中心', section: 'messages' },
  'message-chat': { title: '私信对话', section: 'messages' },
  login: { title: '登录', section: 'auth' },
  register: { title: '注册', section: 'auth' },
  forgot: { title: '找回密码', section: 'auth' },
  support: { title: '帮助与客服', section: 'help' },
  'support-ticket': { title: '客服工单', section: 'help' },
  policies: { title: '用户协议与交易规则', section: 'help' },
  official: { title: '麦麦官方', section: 'official' },
  'official-article': { title: '官方内容', section: 'official' },
  'admin-dashboard': { title: '平台总览', section: 'admin' },
  'admin-categories': { title: '分类维护', section: 'admin' },
  'admin-finance': { title: '资金与对账', section: 'admin' },
  'admin-trade-todos': { title: '交易待办', section: 'admin' },
  'admin-support': { title: '客服工单', section: 'admin' },
  'admin-community': { title: '社区与举报', section: 'admin' },
  'admin-seller-apps': { title: '卖家申请', section: 'admin' },
  'admin-product-review': { title: '商品审核', section: 'admin' },
  'admin-aftersales': { title: '售后仲裁', section: 'admin' },
  'admin-users': { title: '用户管理', section: 'admin' },
  'admin-audit-logs': { title: '审计日志', section: 'admin' },
  'admin-official': { title: '官方内容管理', section: 'admin' },
};
export const sectionInfo: Record<
  string,
  { label: string; links: { to: string; label: string }[] }
> = {
  transaction: { label: '交易详情', links: [] },
  market: {
    label: '商城',
    links: [
      { to: '/search', label: '全部闲置' },
      { to: '/community/demands', label: '求购广场' },
      { to: '/official', label: '新手指南' },
    ],
  },
  buyer: {
    label: '买家交易',
    links: [
      { to: '/orders', label: '我买到的' },
      { to: '/cart', label: '购物车' },
      { to: '/me/bargains', label: '我的议价' },
      { to: '/me/aftersales', label: '售后记录' },
    ],
  },
  seller: {
    label: '卖家工作台',
    links: [
      { to: '/seller/products', label: '商品管理' },
      { to: '/seller/orders', label: '我卖出的' },
      { to: '/seller/bargains', label: '收到的议价' },
      { to: '/seller/aftersales', label: '售后处理' },
      { to: '/publish', label: '发布闲置' },
    ],
  },
  account: {
    label: '个人中心',
    links: [
      { to: '/me', label: '资料与地址' },
      { to: '/me/community', label: '收藏与关注' },
      { to: '/orders', label: '买家交易' },
      { to: '/seller/products', label: '卖家工作台' },
    ],
  },
  messages: {
    label: '消息中心',
    links: [
      { to: '/messages', label: '私信收件箱' },
      { to: '/support', label: '联系平台客服' },
    ],
  },
  community: {
    label: '求购社区',
    links: [
      { to: '/community/demands', label: '大家在找' },
      { to: '/community/demands?mine=1', label: '我的求购' },
      { to: '/search', label: '返回商城' },
    ],
  },
  help: {
    label: '帮助中心',
    links: [
      { to: '/support', label: '帮助与客服' },
      { to: '/policies', label: '协议与规则' },
      { to: '/official', label: '官方内容' },
    ],
  },
  official: {
    label: '麦麦官方',
    links: [
      { to: '/official', label: '公告与指南' },
      { to: '/support', label: '帮助与客服' },
      { to: '/policies', label: '交易规则' },
    ],
  },
  admin: { label: '管理后台', links: [] },
  auth: { label: '账号', links: [] },
};
