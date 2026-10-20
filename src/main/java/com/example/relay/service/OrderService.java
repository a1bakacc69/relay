package com.example.relay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.relay.client.ShopifyClient;
import com.example.relay.client.SupplierClient;
import com.example.relay.common.BizException;
import com.example.relay.dto.ShopifyOrderDto;
import com.example.relay.dto.SupplierOrderQueryResponse;
import com.example.relay.dto.SupplierOrderRequest;
import com.example.relay.entity.OrderItem;
import com.example.relay.entity.PlatformOrder;
import com.example.relay.entity.ProductMapping;
import com.example.relay.entity.ProductVariant;
import com.example.relay.enums.OrderStatus;
import com.example.relay.enums.SaveResult;
import com.example.relay.mapper.OrderItemMapper;
import com.example.relay.mapper.PlatformOrderMapper;
import com.example.relay.mapper.ProductMappingMapper;
import com.example.relay.mapper.ProductVariantMapper;
import com.example.relay.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@Service
public class OrderService {

    @Autowired
    private PlatformOrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private ShopifyClient shopifyClient;

    @Autowired
    private OrderSyncService orderSyncService;

    @Autowired
    private SupplierClient supplierClient;

    @Autowired
    private ProductMappingMapper productMappingMapper;

    @Autowired
    private ProductVariantMapper productVariantMapper;

    // 去 Shopify 拉全量，逐条同步进库；返回一句话统计
    public String pullOrders(){
        List<ShopifyOrderDto> ordersdto = shopifyClient.fetchOrders();

        int inserted = 0;
        int updated = 0;
        for (ShopifyOrderDto order : ordersdto) {
            if (orderSyncService.saveOrder(order) == SaveResult.INSERTED) {
                inserted++;
            } else {
                updated++;
            }
        }
        return "共拉到订单数：" + ordersdto.size()
                + "\n新增：" + inserted + " 条"
                + "\n更新：" + updated + " 条";
    }

    public List<OrderVO> listOrders(){
        List<PlatformOrder> orders = orderMapper.selectList(null);

        List<OrderVO> list = new ArrayList<>();

        for(PlatformOrder order : orders) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);
            LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(OrderItem::getOrderId, orderVO.getId());
            List<OrderItem> items = orderItemMapper.selectList(wrapper);
            orderVO.setItems(items);
            list.add(orderVO);
        }
        return list;
    }

    public OrderVO getOrderById(Long id) {
        PlatformOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }

        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order,vo);
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId,vo.getId());
        List<OrderItem> items = orderItemMapper.selectList(wrapper);
        vo.setItems(items);
        return vo;
    }

    public String placeOrder(Long orderId){

        PlatformOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }

        // 幂等，防止重复点击
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new BizException(400, "订单当前状态为「" + order.getStatus() + "」，不能重复下单");
        }
        // 空订单不给下单
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemMapper.selectList(wrapper);
        if (items.isEmpty()) {
            throw new BizException(400, "订单没有商品明细，无法下单");
        }

        SupplierOrderRequest req = new SupplierOrderRequest();
        req.setClientOrderNo(order.getId());        // 幂等键 = orders.id
        req.setReceiverName(order.getShippingName());
        req.setReceiverPhone(order.getShippingPhone());
        req.setReceiverAddress1(order.getShippingAddress1());
        req.setReceiverCity(order.getShippingCity());
        req.setReceiverZip(order.getShippingZip());
        req.setReceiverProvince(order.getShippingProvince());
        req.setReceiverCountryCode(order.getShippingCountryCode());
        // 明细抽成单独方法。要带上平台 —— 映射是按 (平台, 变体ID) 查的
        req.setItems(toSupplierItems(items, order.getPlatform()));

        String supplierOrderId = supplierClient.placeOrder(req);

        orderMapper.updatePlaced(orderId, supplierOrderId, OrderStatus.ORDERED.name());

        return "下单成功，供货商单号：" + supplierOrderId;

    }

    /**
     * 把订单明细翻译成供应商要的样子。
     *
     * <p><b>这个方法是整个映射机制真正起作用的地方。</b>
     * 订单明细里带的是平台侧的变体ID，供应商要的是他自己的规格ID，
     * 中间必须过一道映射表：
     *
     * <pre>
     *   order_items.variant_id  ──映射表──►  product_variants.id  ──►  supplier_variant_id
     * </pre>
     *
     * <p>映射缺失时<b>不跳过、不猜</b>：全部收集起来一次性抛错，
     * 让用户一眼看到还差哪几个，而不是改一个报一个。
     * 猜着发更是绝对不行的 —— 发错货的代价比下单失败大得多。
     *
     * @param items    订单明细
     * @param platform 订单来自哪个平台。同一个变体ID在不同平台上是两回事，必须带上
     */
    private List<SupplierOrderRequest.Item> toSupplierItems(List<OrderItem> items, String platform) {

        // ① 把订单里出现过的平台变体ID收起来，去重 —— 后面一次查完
        List<Long> platformVariantIds = items.stream()
                .map(OrderItem::getVariantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // ② 第一跳：平台变体 → 本地规格。
        //    一次 IN 查询捞完，不是一条明细查一次 —— 那会变成 N+1 次数据库往返
        Map<Long, Long> platformToLocal = new HashMap<>();
        if (!platformVariantIds.isEmpty()) {
            List<ProductMapping> mappings = productMappingMapper.selectList(
                    new LambdaQueryWrapper<ProductMapping>()
                            .eq(ProductMapping::getPlatform, platform)
                            .in(ProductMapping::getPlatformVariantId, platformVariantIds));
            for (ProductMapping m : mappings) {
                platformToLocal.put(m.getPlatformVariantId(), m.getLocalVariantId());
            }
        }

        // ③ 第二跳：本地规格 → 供应商规格。同样一次查完
        Map<Long, Long> localToSupplier = new HashMap<>();
        if (!platformToLocal.isEmpty()) {
            List<ProductVariant> variants =
                    productVariantMapper.selectBatchIds(platformToLocal.values());
            for (ProductVariant v : variants) {
                localToSupplier.put(v.getId(), v.getSupplierVariantId());
            }
        }

        // ④ 组装。缺映射的先全部收集，最后统一报错
        List<SupplierOrderRequest.Item> list = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (OrderItem item : items) {

            Long supplierVariantId = null;

            if (item.getVariantId() != null) {
                Long localVariantId = platformToLocal.get(item.getVariantId());
                if (localVariantId != null) {
                    supplierVariantId = localToSupplier.get(localVariantId);
                }
            }

            if (supplierVariantId == null) {
                missing.add(describeMissing(item));
                continue;
            }

            SupplierOrderRequest.Item si = new SupplierOrderRequest.Item();
            si.setTitle(item.getTitle());
            si.setQuantity(item.getQuantity());
            si.setPrice(item.getPrice());
            si.setSupplierVariantId(supplierVariantId);
            list.add(si);
        }

        if (!missing.isEmpty()) {
            throw new BizException(400,
                    "订单里有 " + missing.size() + " 个变体还没建立映射，无法下单：\n - "
                            + String.join("\n - ", missing)
                            + "\n调 GET /mappings/unbound-variants 能看到还差哪些，绑好再来");
        }

        return list;
    }

    /** 描述一个没映射上的明细，报错信息里要让用户认得出是哪件货 */
    private String describeMissing(OrderItem item) {
        if (item.getVariantId() == null) {
            return "「" + item.getTitle() + "」平台侧没给变体ID，数据不全";
        }
        return "「" + item.getTitle() + "」"
                + (item.getVariantTitle() == null ? "" : " / " + item.getVariantTitle())
                + "（platformVariantId=" + item.getVariantId() + "）";
    }

    // 去供货商那儿问发货没，发了就把运单号写回来
    @Transactional
    public String refreshShipping(Long orderId) {

        PlatformOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }

        if (!OrderStatus.ORDERED.name().equals(order.getStatus())) {
            throw new BizException(400, "订单当前状态为「" + order.getStatus() + "」，无需查询物流");
        }

        if (order.getSupplierOrderId() == null) {
            throw new BizException(400, "订单还没有供货商单号，无法查询物流");
        }

        SupplierOrderQueryResponse.Data data = supplierClient.queryOrder(order.getSupplierOrderId());

        // 还没发货不是错误，正常返回
        if (!OrderStatus.SHIPPED.name().equals(data.getStatus())) {
            return "供货商尚未发货（当前状态：" + data.getStatus() + "）";
        }

        orderMapper.updateShipped(
                orderId,
                data.getTrackingNumber(),
                data.getTrackingCompany(),
                OrderStatus.SHIPPED.name()
        );

        return "已获取运单号：" + data.getTrackingNumber() + "（" + data.getTrackingCompany() + "）";
    }

    // 回写运单号给 Shopify，闭环最后一步。故意不加 @Transactional：
    // 只有最后一次写库，中间夹着两次 HTTP，包事务反而占着数据库连接。
    // 另外远程成功、本地写库失败这种「一半成功」，事务也救不了，得靠补偿。
    public String pushTracking(Long orderId) {

        PlatformOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(404, "订单不存在");
        }

        if (!OrderStatus.SHIPPED.name().equals(order.getStatus())) {
            throw new BizException(400, "订单当前状态为「" + order.getStatus() + "」，无需回写");
        }

        if (order.getTrackingNumber() == null || order.getTrackingNumber().isBlank()) {
            throw new BizException(400, "订单还没有运单号，无法回写平台");
        }

        Long fulfillmentOrderId = shopifyClient.fetchFulfillmentOrderId(order.getPlatformOrderId());
        if (fulfillmentOrderId == null) {
            throw new BizException(500, "Shopify 未返回 fulfillment_order_id，无法回写");
        }

        shopifyClient.createFulfillment(
                fulfillmentOrderId,
                order.getTrackingNumber(),
                order.getTrackingCompany()
        );

        orderMapper.updateSynced(orderId, OrderStatus.SYNCED.name());

        // 平台侧的真相只能从平台读回来。直接写 "fulfilled" 是猜的：
        // 平台可能只做了部分履约（partial），猜出来的值比空值更坏 ——
        // 空值至少说明「不知道」，猜的值看起来像已经知情。
        String readBack;
        try {
            String platformStatus = shopifyClient.fetchFulfillmentStatus(order.getPlatformOrderId());
            orderMapper.updateFulfillmentStatus(orderId, platformStatus);
            readBack = "（平台履约状态：" + platformStatus + "）";
        } catch (Exception e) {
            // 运单号已经写进平台了，不能因为回读失败就让调用方以为整件事没成
            readBack = "（履约状态回读失败，下次同步会修正）";
        }

        return "已回写平台：运单号 " + order.getTrackingNumber()
                + "（" + order.getTrackingCompany() + "）" + readBack;
    }

}
