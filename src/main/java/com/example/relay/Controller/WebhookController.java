package com.example.relay.Controller;

import com.example.relay.common.ShopifyWebhookVerifier;
import com.example.relay.dto.ShopifyOrderDto;
import com.example.relay.enums.SaveResult;
import com.example.relay.service.OrderSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收 Shopify 主动推送的 webhook。
 *
 * 和别的 Controller 最大的不同：这里不返回 R 结构，只返回 HTTP 状态码。
 * Shopify 只认「2xx = 送达」，body 里是什么它不看。
 */
@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ShopifyWebhookVerifier verifier;
    private final OrderSyncService orderSyncService;
    private final ObjectMapper objectMapper;

    public WebhookController(ShopifyWebhookVerifier verifier,
                             OrderSyncService orderSyncService,
                             ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.orderSyncService = orderSyncService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook/orders/create")
    public ResponseEntity<Void> onOrderCreate(
            @RequestHeader(value = "X-Shopify-Hmac-Sha256", required = false) String hmac,
            @RequestHeader(value = "X-Shopify-Topic", required = false) String topic,
            @RequestBody byte[] rawBody) {

        // 1. 验签：必须拿原始字节算，不能用解析后的对象
        if (!verifier.verify(rawBody, hmac)) {
            log.warn("webhook 验签失败，已拒绝，topic={}", topic);
            return ResponseEntity.status(401).build();
        }

        // 2. 只处理订单创建。其他 topic（如 carts/create）返回 200 —— Shopify 认 2xx 为送达，
        // 返回 4xx 会判定失败并反复重推。这里要的是「收到了但不管」，不是「处理出错」
        if (!"orders/create".equals(topic)) {
            log.warn("忽略非订单创建事件，topic={}", topic);
            return ResponseEntity.ok().build();
        }

        // 3. 解析（验签通过之后才敢解析）
        ShopifyOrderDto dto;
        try {
            dto = objectMapper.readValue(rawBody, ShopifyOrderDto.class);
        } catch (Exception e) {
            log.error("webhook 请求体解析失败，topic={}", topic, e);
            return ResponseEntity.badRequest().build();
        }

        // 4. 入库：复用拉单那条路，唯一键保证重复推送不会写重。
        // 异常必须在这里拦住转成 500 —— 全局异常处理器返回的是 HTTP 200，
        // Shopify 会当成「已送达」不再重推，订单就静默丢了。
        try {
            SaveResult result = orderSyncService.saveOrder(dto);
            log.info("webhook 收到订单 {}，结果 {}", dto.getId(), result);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("webhook 处理订单 {} 失败，等待 Shopify 重推", dto.getId(), e);
            return ResponseEntity.status(500).build();
        }
    }
}
