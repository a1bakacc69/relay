import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 供应商服务（8081）。
 *
 * 单独开一个实例，是因为它和 Relay 不是一个服务。
 * 页面上只有一处会用到它 —— 商品页要展示「供应商的货架」让用户挑一个导入，
 * 而 Relay 目前只提供了「按 id 导入」，没有「列出供应商商品」的转发接口。
 *
 * 真正下单、查物流走的都是 Relay，前端不直接碰供应商。
 */
const supplier = axios.create({
  baseURL: '/supplier-api',
  timeout: 30000
})

supplier.interceptors.response.use(
  (response) => {
    const r = response.data
    if (r === null || typeof r === 'undefined' || typeof r.code === 'undefined') {
      return r
    }
    if (r.code === 200) {
      return r.data
    }
    ElMessage.error(r.msg || '供应商接口返回失败')
    return Promise.reject(new Error(r.msg || '供应商接口返回失败'))
  },
  (error) => {
    ElMessage.error(
      error.response ? `供应商请求失败（HTTP ${error.response.status}）` : '连不上供应商服务，确认 8081 起来了没'
    )
    return Promise.reject(error)
  }
)

/** 供应商商品列表（分页 + 关键词） */
export const listSupplierProducts = (params) => supplier.get('/supplier/products', { params })

/** 供应商商品详情（含规格和库存） */
export const getSupplierProduct = (id) => supplier.get(`/supplier/products/${id}`)
