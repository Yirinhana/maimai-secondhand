import {
  createRouter,
  createWebHistory,
  START_LOCATION,
} from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '../shared/stores/auth';
import { waitForAnchor } from '../shared/anchorScroll';
import { hasSeenWelcome } from '../modules/catalog/welcomeSession';
import {
  startNavigation,
  finishNavigation,
  failNavigation,
} from '../shared/navigationFeedback';

const routes: RouteRecordRaw[] = [
  {
    path: '/official',
    name: 'official',
    component: () => import('../modules/official/OfficialListPage.vue'),
  },
  {
    path: '/official/:slug',
    name: 'official-article',
    component: () => import('../modules/official/OfficialArticlePage.vue'),
  },
  {
    path: '/me/closure',
    name: 'account-closure',
    component: () => import('../modules/account/AccountClosurePage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/policies',
    name: 'policies',
    component: () => import('../modules/policies/PoliciesPage.vue'),
  },
  {
    path: '/support',
    name: 'support',
    component: () => import('../modules/support/SupportListPage.vue'),
  },
  {
    path: '/support/tickets/:id',
    name: 'support-ticket',
    component: () => import('../modules/support/SupportTicketPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/community/demands',
    name: 'demands',
    component: () => import('../modules/community/DemandsPage.vue'),
  },
  {
    path: '/me/community',
    name: 'my-community',
    component: () => import('../modules/community/MyCommunityPage.vue'),
    meta: { requiresAuth: true },
  },
  // 商品（catalog）
  {
    path: '/welcome',
    name: 'welcome',
    component: () => import('../modules/catalog/WelcomePage.vue'),
    meta: { standalone: true, title: '认识麦麦' },
  },
  {
    path: '/',
    name: 'home',
    component: () => import('../modules/catalog/HomePage.vue'),
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('../modules/catalog/SearchPage.vue'),
  },
  {
    path: '/products/:id',
    name: 'product-detail',
    component: () => import('../modules/catalog/ProductDetailPage.vue'),
  },
  {
    path: '/sellers/:id',
    name: 'seller',
    component: () => import('../modules/catalog/SellerPage.vue'),
  },
  {
    path: '/publish',
    name: 'publish',
    component: () => import('../modules/catalog/ProductEditPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/publish/:id',
    name: 'publish-edit',
    component: () => import('../modules/catalog/ProductEditPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/seller',
    name: 'seller-dashboard',
    component: () => import('../modules/catalog/SellerDashboardPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/seller/products',
    name: 'my-products',
    component: () => import('../modules/catalog/MyProductsPage.vue'),
    meta: { requiresAuth: true },
  },
  // 认证（auth）
  {
    path: '/login',
    name: 'login',
    component: () => import('../modules/auth/LoginPage.vue'),
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../modules/auth/RegisterPage.vue'),
  },
  {
    path: '/forgot',
    name: 'forgot',
    component: () => import('../modules/auth/ForgotPage.vue'),
  },
  // 交易（trade）
  {
    path: '/experience-pay/:token',
    name: 'experience-cashier',
    component: () => import('../modules/trade/ExperienceCashierPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/me/bargains',
    name: 'my-bargains',
    component: () => import('../modules/trade/MyBargainsPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/seller/bargains',
    name: 'seller-bargains',
    component: () => import('../modules/trade/SellerBargainsPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/cart',
    name: 'cart',
    component: () => import('../modules/trade/CartPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/checkout',
    name: 'checkout',
    component: () => import('../modules/trade/CheckoutPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/orders',
    name: 'my-orders',
    component: () => import('../modules/trade/MyOrdersPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/orders/:orderNo',
    name: 'order-detail',
    component: () => import('../modules/trade/OrderDetailPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/seller/orders',
    name: 'seller-orders',
    component: () => import('../modules/trade/SellerOrdersPage.vue'),
    meta: { requiresAuth: true },
  },
  // 售后（aftersales）
  {
    path: '/me/aftersales',
    name: 'my-aftersales',
    component: () => import('../modules/aftersales/MyAftersalesPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/aftersales/:id',
    name: 'aftersale-detail',
    component: () =>
      import('../modules/aftersales/AftersaleDetailPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/seller/aftersales',
    name: 'seller-aftersales',
    component: () =>
      import('../modules/aftersales/SellerAftersalesPage.vue'),
    meta: { requiresAuth: true },
  },
  // 账号（account）
  {
    path: '/me',
    name: 'account',
    component: () => import('../modules/account/AccountPage.vue'),
    meta: { requiresAuth: true },
  },
  // 私信（messaging）
  {
    path: '/messages',
    name: 'messages',
    component: () =>
      import('../modules/messaging/ConversationListPage.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/messages/:id',
    name: 'message-chat',
    component: () => import('../modules/messaging/ChatPage.vue'),
    meta: { requiresAuth: true },
  },
  // 后台（admin）
  {
    path: '/admin',
    component: () => import('../modules/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, admin: true },
    children: [
      { path: 'orders', name: 'admin-orders', component: () => import('../modules/admin/OrdersPage.vue') },
      { path: 'orders/:orderNo', name: 'admin-order-detail', component: () => import('../modules/admin/OrdersPage.vue') },
      {
        path: 'official',
        name: 'admin-official',
        component: () =>
          import('../modules/official/AdminOfficialPage.vue'),
        meta: { title: '官方内容管理' },
      },
      {
        path: 'categories',
        name: 'admin-categories',
        component: () => import('../modules/admin/CategoriesPage.vue'),
      },
      {
        path: 'finance',
        name: 'admin-finance',
        component: () => import('../modules/admin/FinancePage.vue'),
      },
      {
        path: 'trade-todos',
        name: 'admin-trade-todos',
        component: () => import('../modules/admin/TradeTodosPage.vue'),
      },
      {
        path: 'support',
        name: 'admin-support',
        component: () => import('../modules/support/SupportListPage.vue'),
      },
      {
        path: 'community',
        name: 'admin-community',
        component: () =>
          import('../modules/admin/CommunityModerationPage.vue'),
      },
      {
        path: '',
        name: 'admin-dashboard',
        component: () => import('../modules/admin/DashboardPage.vue'),
      },
      {
        path: 'seller-apps',
        name: 'admin-seller-apps',
        component: () => import('../modules/admin/SellerAppsPage.vue'),
      },
      {
        path: 'products',
        name: 'admin-product-review',
        component: () => import('../modules/admin/ProductReviewPage.vue'),
      },
      {
        path: 'aftersales',
        name: 'admin-aftersales',
        component: () =>
          import('../modules/admin/AdminAftersalesPage.vue'),
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('../modules/admin/UsersPage.vue'),
      },
      {
        path: 'audit-logs',
        name: 'admin-audit-logs',
        component: () => import('../modules/admin/AuditLogsPage.vue'),
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('../shared/components/NotFoundPage.vue'),
  },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (to, from, savedPosition) => {
    if (savedPosition) return savedPosition;
    if (to.hash)
      return waitForAnchor(
        to.hash,
        () => router.currentRoute.value.fullPath === to.fullPath,
      );
    if (to.path === from.path) return false;
    return { top: 0 };
  },
});

router.beforeEach(async (to, from) => {
  startNavigation();
  // Only greet a fresh visit to the root. Shared links keep their destination.
  if (
    to.name === 'home' &&
    from === START_LOCATION &&
    !to.hash &&
    Object.keys(to.query).length === 0 &&
    !hasSeenWelcome()
  )
    return { name: 'welcome', replace: true };

  if (!to.meta.requiresAuth && !to.meta.admin) return true;

  const auth = useAuthStore();
  if (!auth.meLoaded) {
    await auth.fetchMe();
  }
  if (!auth.me) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  if (to.meta.admin && !auth.isAdmin) {
    return { name: 'home' };
  }
  return true;
});

router.afterEach(() => finishNavigation());
router.onError((_error, to) => failNavigation(to.fullPath));
