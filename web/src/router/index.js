import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layout/MainLayout.vue'
import { isLoggedIn } from '../utils/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    // meta.public 是这块唯一的「不用登录」标记，守卫只认它
    meta: { public: true }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/orders',
    children: [
      {
        path: 'orders',
        name: 'orderList',
        component: () => import('../views/OrderList.vue'),
        meta: { title: '订单', subtitle: '从平台拉单，下单给供应商，再把运单号回写平台' }
      },
      {
        path: 'orders/:id',
        name: 'orderDetail',
        component: () => import('../views/OrderDetail.vue'),
        meta: { title: '订单详情', parent: '/orders' }
      },
      {
        path: 'products',
        name: 'productList',
        component: () => import('../views/ProductList.vue'),
        meta: { title: '商品', subtitle: '从供应商导入商品，改成我们想卖的样子，然后定价' }
      },
      {
        path: 'products/:id',
        name: 'productDetail',
        component: () => import('../views/ProductDetail.vue'),
        meta: { title: '商品编辑', parent: '/products' }
      },
      {
        path: 'mappings',
        name: 'mappingList',
        component: () => import('../views/MappingList.vue'),
        meta: { title: '映射', subtitle: '把平台上的变体绑到我们的规格上，下单才知道找哪件货' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/orders' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 路由守卫：没 token 的一律送去登录页。
 *
 * <p>注意这<b>只是体验层</b>：真正拦得住的是后端 Security。
 * 前端守卫能被绕过（手动改 localStorage 就行），所以它只负责「别让用户看见空页面」，
 * 数据安全永远靠服务端验 token。
 */
router.beforeEach((to) => {
  const loggedIn = isLoggedIn()

  if (!to.meta.public && !loggedIn) {
    // 记下原本要去哪儿，登录成功后送回去
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已经登录还去登录页，直接回首页
  if (to.path === '/login' && loggedIn) {
    return { path: '/' }
  }

  return true
})

export default router
