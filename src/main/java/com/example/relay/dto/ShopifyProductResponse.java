package com.example.relay.dto;

import lombok.Data;

import java.util.List;

/**
 * Shopify 商品的创建/更新响应（POST|PUT /admin/api/2024-07/products.json）。
 *
 * <p>只留发布流程真正用得上的字段：商品 id 要落库，变体 id 要写映射表。
 * 其余字段 Jackson 会静默丢掉，不占内存。
 *
 * <p><b>为什么变体的 title 很重要：</b>Shopify 对只有一个选项的商品，
 * 变体的 title 就等于我们传过去的 option1（500ml / 750ml / 1000ml）。
 * 发布完拿它跟本地 variantTitle 对一下，就知道哪个 Shopify 变体对应哪条本地规格，
 * 映射关系不用靠“返回值顺序和我传的顺序一致”这种不可靠的假设。
 */
@Data
public class ShopifyProductResponse {

    private Product product;

    @Data
    public static class Product {

        /** ★ Shopify 商品 id，发布成功后写回 products.platform_product_id */
        private Long id;

        private List<Variant> variants;
    }

    @Data
    public static class Variant {

        /** ★ Shopify 变体 id，写进 product_mappings.platform_variant_id */
        private Long id;

        /** 对应我们传的 option1，即本地 variantTitle */
        private String title;

        /** 我们传过去的货号，Shopify 原样保留 */
        private String sku;

        /** 字符串，如 "112.50" */
        private String price;
    }
}
