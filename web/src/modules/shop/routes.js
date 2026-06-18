import { defineAsyncComponent } from 'vue';

const lazy = loader => defineAsyncComponent(loader);

const Shop = lazy(() => import('@/modules/shop/pages/Shop'));
const ShopAccountLayout = lazy(() => import('@/modules/shop/layouts/ShopAccountLayout.vue'));
const AccountOrders = lazy(() => import('@/modules/shop/pages/Account/Orders.vue'));
const AccountTickets = lazy(() => import('@/modules/shop/pages/Account/Tickets.vue'));
const ShopEntry = lazy(() => import('@/modules/shop/pages/Shop/pages/ShopEntry.vue'));
const ShopItemList = lazy(() => import('@/modules/shop/pages/Shop/pages/ShopItemList.vue'));
const ShopItemDetail = lazy(() => import('@/modules/shop/pages/Shop/pages/ShopItemDetail.vue'));
const ShopManageWorkbench = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/ShopManageWorkbench.vue'));
const ShopManage = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/ShopManage.vue'));
const ItemManage = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/ItemManage.vue'));
const GroupManage = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/GroupManage.vue'));
const TicketManage = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/TicketManage.vue'));
const OrderManage = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/OrderManage.vue'));
const UserManage = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/UserManage.vue'));
const CouponManage = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/CouponManage.vue'));
const ContractManage = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/ContractManage.vue'));
const GitRepositoryManage = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/GitRepositoryManage.vue'));
const ShopManageLayout = lazy(() => import('@/modules/shop/pages/Shop/pages/manage/index.vue'));

const shopMeta = meta => ({
  ...(meta || {}),
  capability: 'shop'
});

export const shopRoutes = {
  '/account/orders': {
    name: '我的订单',
    component: AccountOrders,
    layout: ShopAccountLayout,
    meta: shopMeta({
      requiresAuth: true
    })
  },
  '/account/tickets': {
    name: '我的工单',
    component: AccountTickets,
    layout: ShopAccountLayout,
    meta: shopMeta({
      requiresAuth: true
    })
  },
  '/shop': {
    name: '飞鱼小铺',
    layout: Shop,
    component: ShopEntry,
    meta: shopMeta(),
    children: {
      '/item-list': {
        name: '商品列表',
        component: ShopItemList,
        meta: shopMeta()
      },
      '/detail/:id': {
        name: '商品详情',
        component: ShopItemDetail,
        meta: shopMeta()
      },
      '/manage': {
        name: '系统管理',
        layout: ShopManageLayout,
        meta: shopMeta({
          requiresAuth: true
        }),
        children: {
          '/workbench': {
            name: '小铺工作台',
            component: ShopManageWorkbench,
            meta: shopMeta()
          },
          '/shops': {
            name: '店铺管理',
            component: ShopManage,
            meta: shopMeta()
          },
          '/groups': {
            name: '商品分组管理',
            component: GroupManage,
            meta: shopMeta()
          },
          '/items': {
            name: '商品管理',
            component: ItemManage,
            meta: shopMeta()
          },
          '/repositories': {
            name: '代码仓库管理',
            component: GitRepositoryManage,
            meta: shopMeta()
          },
          '/orders': {
            name: '订单管理',
            component: OrderManage,
            meta: shopMeta()
          },
          '/users': {
            name: '用户管理',
            component: UserManage,
            meta: shopMeta()
          },
          '/coupons': {
            name: '优惠券管理',
            component: CouponManage,
            meta: shopMeta()
          },
          '/contracts': {
            name: '合同管理',
            component: ContractManage,
            meta: shopMeta()
          },
          '/tickets': {
            name: '工单管理',
            component: TicketManage,
            meta: shopMeta()
          }
        }
      }
    }
  }
};
