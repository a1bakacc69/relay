package com.example.relay.vo;

import com.example.relay.entity.ProductVariant;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情，给前端看的形状。
 *
 * <p>比 Product 实体多了最下面那一层：把规格数组也装进来了。
 * 列表接口不返回这个 —— 列表轻、详情重，这是接口设计上一直在用的取舍。
 */
@Data
public class ProductVO {

    private Long id;

    /** 供应商侧的商品 id。前端想跳去供应商那边看原商品时要用 */
    private Long supplierProductId;

    /**
     * ★ Shopify 商品 id。null 表示还没发布过。
     * 前端拿它判断按钮该显示「发布」还是「更新」
     */
    private Long platformProductId;

    private String title;

    private String description;

    private String imageUrl;

    private String category;

    /** 加价倍数。null 表示还没定价，这时每条规格的 salePrice 也都是空的 */
    private BigDecimal priceRatio;

    /** DRAFT 已导入未定价 / ON_SALE 在售 / OFF_SHELF 已下架 */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 全部规格。每条上都带着 costPrice 和 salePrice，前端一眼能看出加了多少 */
    private List<ProductVariant> variants;
}
