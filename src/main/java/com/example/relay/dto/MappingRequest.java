package com.example.relay.dto;

import lombok.Data;

/**
 * 建立一条映射关系的请求体。
 *
 * <p>三个字段缺一不可：哪边的货（platformVariantId + platform）、
 * 对到我们这边的哪条规格（localVariantId）。
 */
@Data
public class MappingRequest {

    /** 我们的规格：product_variants.id */
    private Long localVariantId;

    /** 平台：SHOPIFY / WOOCOMMERCE */
    private String platform;

    /** 平台侧变体ID。从 /mappings/unbound-variants 里抄一个过来 */
    private Long platformVariantId;
}
