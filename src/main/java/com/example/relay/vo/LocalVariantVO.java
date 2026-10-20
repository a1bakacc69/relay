package com.example.relay.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 我们自己的规格，带上一层商品名。
 *
 * <p>专门给「建映射时选哪条规格」这个下拉框用。
 *
 * <p>为什么不直接返回 {@link com.example.relay.entity.ProductVariant}：
 * 光看规格名是认不出货的 —— 满屏都是 500ml / 750ml，前端得知道它属于哪个商品
 * 才能显示成「保温杯 · 500ml」。少这一个字段，前端就得为每条规格再查一次商品，
 * 那就是 N+1。
 */
@Data
public class LocalVariantVO {

    /** product_variants.id —— 建映射时填的 localVariantId 就是它 */
    private Long id;

    private String productTitle;

    private String variantTitle;

    /** 采购价，给用户做参考 */
    private BigDecimal costPrice;

    /** 售价。还没定价时是 null */
    private BigDecimal salePrice;

    /** 已经对上的供应商规格。null 表示这个本地规格还没绑过平台变体 */
    private Long supplierVariantId;
}
