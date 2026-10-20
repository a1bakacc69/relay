package com.example.relay.vo;

import lombok.Data;

/**
 * 「出现在订单里、但还没建映射」的一个变体。
 *
 * <p>这是映射管理页最有用的一块数据：不用用户自己猜还差哪些没绑，
 * 拿真实订单算给他看。
 */
@Data
public class UnboundVariantVO {

    /** ★ 平台侧变体ID —— 绑定时要填的 platformVariantId 就是它 */
    private Long variantId;

    private String platform;

    /** 商品名，帮用户认出来这是哪件货 */
    private String title;

    /** 规格名，如 Sunset。可能为空 —— 平台上没填规格名就是这样 */
    private String variantTitle;

    /** 在订单里出现过几次。出现得多的先绑，优先级一眼看得出来 */
    private Integer occurrences;
}
