package com.example.relay.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * 发给供货商平台的下单请求。
 * 字段与供货商侧的 PlaceOrderRequest 一一对应（契约由对方定义，我们照抄）。
 */
@Data
public class SupplierOrderRequest {

    /** 幂等键 = orders.id */
    private Long clientOrderNo;

    private String receiverName;
    private String receiverPhone;
    private String receiverAddress1;
    private String receiverCity;
    private String receiverZip;
    private String receiverProvince;
    private String receiverCountryCode;

    private List<Item> items;

    /** 一行商品。嵌套类：因为它只在「请求」里出现，没必要单独一个文件 */
    @Data
    public static class Item {
        private String title;
        private Integer quantity;
        private BigDecimal price;

        /**
         * ★ 供应商侧的规格 ID（supplier_product_variants.id），不是平台侧的变体 ID。
         *
         * <p>名字里带 supplier 是故意的：字段叫 variantId 的时候，
         * 谁都会顺手把订单里的 platform variantId 塞进来 —— 那正是改造前的写法，
         * 供应商收到一个他库里根本不存在的号，还照样「下单成功」。
         *
         * <p>供应商侧的 PlaceOrderItem 必须叫同一个名字。两边对不上的话，
         * Jackson 遇到不认识的字段会<b>静默丢掉</b> —— 对方收到 null，不报错，
         * 直接发错货。最怕的就是这种不报错的错。
         */
        private Long supplierVariantId;
    }
}
