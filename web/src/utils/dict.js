/**
 * 状态字典 —— 把后端存的英文枚举翻成给人看的中文和颜色。
 *
 * 为什么不直接显示英文：这一列是运营看的最多的东西，
 * 满屏 PENDING / ORDERED 得在脑子里翻译一遍，容易看错。
 */

export const ORDER_STATUS = {
  PENDING: { label: '待下单', type: 'info' },
  ORDERED: { label: '已下单', type: 'warning' },
  SHIPPED: { label: '已发货', type: 'primary' },
  SYNCED: { label: '已回写', type: 'success' }
}

export const PRODUCT_STATUS = {
  DRAFT: { label: '未定价', type: 'info' },
  ON_SALE: { label: '在售', type: 'success' },
  OFF_SHELF: { label: '已下架', type: 'danger' }
}

export const orderStatus = (s) => ORDER_STATUS[s] || { label: s || '-', type: 'info' }
export const productStatus = (s) => PRODUCT_STATUS[s] || { label: s || '-', type: 'info' }

/** 订单状态流转的下一步提示，用在详情页引导用户点哪个按钮 */
export const NEXT_ACTION = {
  PENDING: { text: '下一步：下单给供应商', action: 'place' },
  ORDERED: { text: '下一步：问供应商发货没', action: 'refresh' },
  SHIPPED: { text: '下一步：把运单号回写平台', action: 'push' },
  SYNCED: { text: '这单走完了', action: null }
}

/** 金额：统一两位小数 */
export function money(v) {
  if (v === null || v === undefined || v === '') return '-'
  return '$' + Number(v).toFixed(2)
}

/** 时间：后端返回 2026-09-21T02:20:16，把 T 换成空格就够了 */
export function dt(v) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 19)
}

/** 大数字 ID 加个等宽字体样式就行，不做千分位（变体 ID 加逗号反而难复制） */
export function shortId(v) {
  return v === null || v === undefined ? '-' : String(v)
}
