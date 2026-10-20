import http from './http'

/** 我们的商品目录 */
export const listProducts = () => http.get('/products/list')

/** 商品详情（含规格） */
export const getProduct = (id) => http.get(`/products/${id}`)

/** 从供应商导入一个商品。同一个供应商商品只会导进来一次 */
export const importProduct = (supplierProductId) =>
  http.post('/products/import', null, { params: { supplierProductId } })

/** 改标题、描述、图片、状态 */
export const updateProduct = (id, data) => http.put(`/products/${id}`, data)

/** 定价：设加价率，重算所有规格的售价 */
export const updatePricing = (id, priceRatio) =>
  http.put(`/products/${id}/pricing`, null, { params: { priceRatio } })

/**
 * 发布到 Shopify。
 * 第一次调用创建商品并自动建映射，之后再调用是更新标题、描述和售价。
 */
export const publishProduct = (id) => http.post(`/products/${id}/publish`)
