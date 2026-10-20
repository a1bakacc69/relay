package com.example.relay.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 供应商商品详情的响应。
 * 对应 JSON：
 * <pre>
 * {"code":200,"msg":"ok","data":{
 *     "id":2,"title":"...","description":"...","imageUrl":"...",
 *     "category":"...","status":"ON_SALE",
 *     "variants":[{"id":3,"variantTitle":"500ml","sku":"BTL-500","price":45.00,"stock":200,"imageUrl":null}]
 * }}
 * </pre>
 *
 * <p>字段要跟供应商那边的 SupplierProductView 严格对上，少一个就反序列化不到。
 * 但<b>不是照抄</b>：那边是人家给自己的表建的对象，这边是我们按自己的需要接的 ——
 * 两边各自独立，谁改谁自己负责。
 */
@Data
public class SupplierProductDto {

    private Integer code;
    private String msg;
    private Data data;

    /** 商品本身 */
    @lombok.Data
    public static class Data {

        /** 供应商侧 supplier_products.id —— 存进我们库就是 supplier_product_id */
        private Long id;

        private String title;
        private String description;
        private String imageUrl;
        private String category;

        /** 供应商侧的上架状态。和我们的 status 同名不同义，不要对拷 */
        private String status;

        /** 全部规格。导入时一个都不能漏，否则订单里买到的规格就没有对应关系 */
        private List<Variant> variants;
    }

    /** 一个规格 */
    @lombok.Data
    public static class Variant {

        /** 供应商侧 supplier_product_variants.id —— 存进我们库就是 supplier_variant_id */
        private Long id;

        private String variantTitle;

        /** 供应商的货号，原样接过来备查 */
        private String sku;

        /** 采购价，不是售价。存进我们库就是 cost_price */
        private BigDecimal price;

        /**
         * 可售库存。接过来只是为了别让 Jackson 报未知字段，
         * <b>我们不存它</b> —— 库存时刻在变又只归供应商管，存一份维护不了的就是放一句谎话。
         * 要用的时候（下单前校验）现去问。
         */
        private Integer stock;

        private String imageUrl;
    }
}
