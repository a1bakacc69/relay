package com.example.relay.dto;

/**
 * 查单条订单的响应外壳：Shopify 返回 {"order": {...}}。
 *
 * <p>复用列表那套 {@link ShopifyOrderDto}，不另建一份 —— 字段完全一样，
 * 多一份就多一个改漏的地方。
 */
public class ShopifyOrderResponse {

    private ShopifyOrderDto order;

    public ShopifyOrderDto getOrder() {
        return order;
    }

    public void setOrder(ShopifyOrderDto order) {
        this.order = order;
    }
}