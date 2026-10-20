package com.example.relay.dto;

import lombok.Data;

/**
 * 改商品营销信息的请求体。
 *
 * <p><b>故意不包含这些字段：</b>
 * <ul>
 *   <li>{@code supplierProductId} —— 身份。改了就跟供应商那边对不上号了</li>
 *   <li>{@code priceRatio} —— 走专门的改价接口，因为它要连带重算所有规格的售价</li>
 * </ul>
 *
 * <p>这是「不给的字段就没有入口」在 API 层的体现：
 * 客户端就算在 JSON 里硬塞一个 supplierProductId，我们也看不见它 —— 这里根本没这个字段。
 * 和 PlatformOrderMapper.updateZoneA 里「SET 不给业务字段留位置」是同一个思路。
 */
@Data
public class ProductUpdateRequest {

    /** 商品标题。我们可以随便改，改了之后重复导入也不会被冲掉 */
    private String title;

    private String description;

    private String imageUrl;

    private String category;

    /** 只能是 DRAFT / ON_SALE / OFF_SHELF，其它值会被拒 */
    private String status;
}
