package com.example.relay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台变体 ↔ 本地规格 的对照表
 * 对应表：product_mappings
 *
 * <p>订单是从平台拉来的，明细里带的是<b>平台侧的 variant_id</b>；
 * 而下单给供应商要的是 <b>supplier_variant_id</b>。这两套 ID 毫无关系，
 * 中间必须有一张表说明「谁对应谁」—— 就是这张。
 *
 * <pre>
 *   平台变体  ──►  我们的规格  ──►  供应商变体
 *   4166417...      product_variants.id=1     supplier_variant_id=3
 *                   ↑ 本表记的是这一段
 * </pre>
 *
 * <p><b>为什么不把 supplier_variant_id 也存一份在这儿：</b>
 * product_variants 上已经有了，两处各存一份迟早对不上。
 * 本表只管「平台 → 本地」这一跳，第二跳顺着 localVariantId 查过去就行。
 */
@Data
@TableName("product_mappings")
public class ProductMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 我们的规格：product_variants.id */
    private Long localVariantId;

    /** 平台：SHOPIFY / WOOCOMMERCE。和 platformVariantId 一起构成唯一键 */
    private String platform;

    /** ★ 平台侧变体ID。订单里 order_items.variant_id 就是它 —— 下单时拿它来查表 */
    private Long platformVariantId;

    private LocalDateTime createTime;

    @TableField(update = "now()")
    private LocalDateTime updateTime;
}
