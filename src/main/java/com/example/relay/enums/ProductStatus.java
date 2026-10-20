package com.example.relay.enums;

import lombok.Getter;

/**
 * 商品在我们这边的状态。
 *
 * <p><b>注意：和供应商侧的 ON_SALE / OFF_SHELF 不是一套东西。</b>
 * 那是「他家还卖不卖」，这是「我们这边定没定价、上没上架」——
 * 同名不同义，导入的时候千万不要对拷。
 */
@Getter
public enum ProductStatus {

    /** 已导入，还没定价 —— 售价算不出来，上不了架 */
    DRAFT("草稿"),

    /** 已定价，在售 */
    ON_SALE("在售"),

    /** 已下架。改价不会自动把它复上架 */
    OFF_SHELF("已下架");

    private final String label;

    ProductStatus(String label) {
        this.label = label;
    }
}
