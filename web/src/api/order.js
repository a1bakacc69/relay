import http from './http'

/** 订单列表（含明细） */
export const listOrders = () => http.get('/orders/list')

/** 订单详情 */
export const getOrder = (id) => http.get(`/orders/${id}`)

/** 从 Shopify 拉一遍全量订单进库 */
export const pullOrders = () => http.post('/orders/pull')

/** 下单给供应商 —— 缺映射时这里会失败，msg 里会列出缺哪几个 */
export const placeOrder = (id) => http.post(`/orders/${id}/place`)

/** 问供应商发货没 */
export const refreshShipping = (id) => http.post(`/orders/${id}/refresh-shipping`)

/** 把运单号回写平台，闭环最后一步 */
export const pushTracking = (id) => http.post(`/orders/${id}/push-tracking`)
