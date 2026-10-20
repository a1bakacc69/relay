package com.example.relay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我们的商品规格表实体
 * 对应表：product_variants
 *
 * <p>和 products 是一对多：一个商品（保温杯）挂多条规格（500ml / 750ml / 1000ml）。
 * 结构上跟 orders / order_items 是同一个套路 —— 一张头表配一张明细表。
 *
 * <p><b>为什么规格要单独一张表：</b>订单里买家买的是<b>具体规格</b>，不是笼统一件商品。
 * 供应商得照着规格发货，所以「我的货 ↔ 供应商的货」这个对应关系必须建在这一层 ——
 * 只做到商品层的话，供应商不知道该发哪个尺寸，会发错货。
 */
@Data
@TableName("product_variants")
public class ProductVariant {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商品，关联 products.id */
    private Long productId;

    /** ★ 来源：供应商侧 supplier_product_variants.id。下单给供应商时传的就是它 */
    private Long supplierVariantId;

    /** 规格名，如 Black / 750ml */
    private String variantTitle;

    /** 供应商的货号，原样存着备查。和我们的 SKU 不是一回事，判断链路里一次都不查它 */
    private String sku;

    /** ★ A 区：采购价，来自供应商。他调价时同步逻辑可以覆盖它 */
    private BigDecimal costPrice;

    /**
     * ★ B 区：售价，我们自己定 = 采购价 × products.price_ratio。
     *
     * <p>同步逻辑永远不碰它。导入时也是 NULL —— 刚导进来还没定价，
     * 等调了改价接口才把值算出来。
     */
    private BigDecimal salePrice;

    /** 规格图，没有就沿用商品主图 */
    private String imageUrl;

    /** 本地创建时间，insert 时不传，交给数据库的 DEFAULT CURRENT_TIMESTAMP */
    private LocalDateTime createTime;

    /** 更新时间。理由同 Product.updateTime —— 那个 update = "now()" 是必须的 */
    @TableField(update = "now()")
    private LocalDateTime updateTime;
}
