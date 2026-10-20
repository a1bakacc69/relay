package com.example.relay.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我们的商品表实体
 * 对应表：products
 *
 * <p>这张表装的是<b>我们自己的商品</b>，不是供应商的。
 * 供应商的货留在人家自己库里，我们这边只拿 supplier_product_id 一根指针指过去 ——
 * 要用的时候现拉，不搬进来。
 *
 * <p>为什么不直接用供应商那份：因为要<b>加价、改标题</b>。
 * 供应商卖的是「45 块的保温杯」，我们卖的是「$24.99 的 Insulated Water Bottle」——
 * 两份档案记的根本不是同一件事。
 */
@Data
@TableName("products")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ★ 来源：供应商侧 supplier_products.id。导入幂等靠它（uk_supplier_product） */
    private Long supplierProductId;

    /**
     * ★ 发布到 Shopify 后拿到的商品 id。
     *
     * <p>NULL 表示还没发布过，NULL / 非 NULL 就是「创建」和「更新」的分界线 ——
     * 发布接口靠它判断该调创建还是调更新，避免重复点几次就多出几个重复商品。
     *
     * <p>这个 id 只有 Shopify 知道，是我们发布完当场记下来的，跟 supplierProductId 无关。
     */
    private Long platformProductId;

    /**
     * 商品标题。导入时取自供应商，<b>导入之后就归我们了</b>。
     *
     * <p>所以重复导入时不能无条件覆盖 —— 那会把用户改好的标题冲回供应商的原名。
     */
    private String title;

    /** 商品描述。性质同 title，导入后归我们 */
    private String description;

    /** 主图，只存外链，不做图片上传 */
    private String imageUrl;

    /** 类目，如 Electronics */
    private String category;

    /** ★ 加价倍数：售价 = 采购价 × 本值。2.00 = 卖两倍价。NULL 表示还没定价 */
    private BigDecimal priceRatio;

    /** DRAFT 已导入未定价 / ON_SALE 在售 / OFF_SHELF 已下架 */
    private String status;

    /** 本地创建时间，insert 时不传，交给数据库的 DEFAULT CURRENT_TIMESTAMP */
    private LocalDateTime createTime;

    /**
     * 本地更新时间，本该由数据库的 ON UPDATE CURRENT_TIMESTAMP 维护。
     *
     * <p>但这里的 {@code update = "now()"} 不能省 —— MyBatis-Plus 的 updateById
     * 会把查出来的旧值原样写进 SQL（{@code update_time = '旧值'}），列一旦被显式赋值，
     * 数据库的 ON UPDATE 就永远不触发，时间戳会一直停在创建的那一刻。
     */
    @TableField(update = "now()")
    private LocalDateTime updateTime;
}
