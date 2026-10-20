package com.example.relay.vo;

import lombok.Data;

/**
 * 一条映射关系，给前端看的形状。
 *
 * <p>比 ProductMapping 实体多了商品名、规格名和供应商变体ID ——
 * 光看两个数字谁也认不出这是哪件货。
 */
@Data
public class MappingVO {

    private Long id;

    private Long localVariantId;

    private String platform;

    private Long platformVariantId;

    // ===== 下面三个是 join 出来的，方便前端显示 =====

    /** 我们这边这件货叫什么 */
    private String productTitle;

    /** 具体是哪个规格 */
    private String variantTitle;

    /** 顺着本地规格查到的供应商变体ID —— 下单时真正发给供应商的那个 */
    private Long supplierVariantId;
}
