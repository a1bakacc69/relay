import http from './http'

/** 已建好的映射（带两边的商品名） */
export const listMappings = () => http.get('/mappings/list')

/** 出现在订单里、但还没绑的变体 —— 数据来自真实订单，不用猜 */
export const listUnboundVariants = () => http.get('/mappings/unbound-variants')

/** 我们有哪些规格可以拿去绑 —— 绑定弹窗里那个下拉框的选项 */
export const listLocalVariants = () => http.get('/mappings/local-variants')

/** 建绑定 */
export const createMapping = (data) => http.post('/mappings', data)

/** 解绑 */
export const deleteMapping = (id) => http.delete(`/mappings/${id}`)
