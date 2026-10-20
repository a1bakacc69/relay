package com.example.relay.client;

import com.example.relay.dto.FulfillmentOrderListResponse;
import com.example.relay.dto.OrderListResponse;
import com.example.relay.dto.ShopifyOrderDto;
import com.example.relay.dto.ShopifyOrderResponse;
import com.example.relay.dto.ShopifyProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责跟 Shopify 打交道：主动去拉订单
 */
@Component
public class ShopifyClient {

    @Value("${shopify.store.url}")
    private String storeUrl;

    @Value("${shopify.access.token}")
    private String accessToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<ShopifyOrderDto> fetchOrders() {
        String url = storeUrl + "/admin/api/2024-07/orders.json?status=any&limit=250";

        // Shopify 靠这个 header 认证
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<OrderListResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, OrderListResponse.class
        );

        OrderListResponse body = response.getBody();
        if (body == null || body.getOrders() == null) {
            return List.of();
        }
        return body.getOrders();
    }

    // 回写运单号要的是 fulfillment_order 的 id，不是我们库里的 order id，所以得先查一次。
    // 查不到返回 null，交给 Service 当错误处理
    public Long fetchFulfillmentOrderId(Long platformOrderId) {

        String url = storeUrl
                + "/admin/api/2024-07/orders/" + platformOrderId + "/fulfillment_orders.json";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<FulfillmentOrderListResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, FulfillmentOrderListResponse.class
        );

        FulfillmentOrderListResponse body = response.getBody();
        if (body == null
                || body.getFulfillmentOrders() == null
                || body.getFulfillmentOrders().isEmpty()) {
            return null;
        }
        // 一个订单可能拆成多张发货单，这里只取第一张
        return body.getFulfillmentOrders().get(0).getId();
    }

    // notify_customer 必须设 false —— 否则 Shopify 会给真实买家发发货邮件。
    // Map 的 key 就是 JSON 字段名，得按 Shopify 的蛇形写法，不能按 Java 习惯写驼峰
    public void createFulfillment(Long fulfillmentOrderId,
                                  String trackingNumber,
                                  String trackingCompany) {

        String url = storeUrl + "/admin/api/2024-07/fulfillments.json";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> trackingInfo = new HashMap<>();
        trackingInfo.put("number", trackingNumber);
        trackingInfo.put("company", trackingCompany);

        Map<String, Object> itemByOrder = new HashMap<>();
        itemByOrder.put("fulfillment_order_id", fulfillmentOrderId);

        Map<String, Object> fulfillment = new HashMap<>();
        fulfillment.put("line_items_by_fulfillment_order", List.of(itemByOrder));
        fulfillment.put("tracking_info", trackingInfo);
        fulfillment.put("notify_customer", false);

        Map<String, Object> body = new HashMap<>();
        body.put("fulfillment", fulfillment);

        restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class
        );
    }

    /**
     * 回读一条订单的履约状态。
     *
     * <p>为什么回读而不是回写成功后直接写 "fulfilled"：平台可能只做了部分履约（partial），
     * 我们自己猜一个值比留空更坏 —— 空值至少说明「不知道」，猜的值看起来像已经知情。
     */
    public String fetchFulfillmentStatus(Long platformOrderId) {

        String url = storeUrl + "/admin/api/2024-07/orders/" + platformOrderId
                + ".json?fields=id,fulfillment_status";

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<ShopifyOrderResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, ShopifyOrderResponse.class
        );

        ShopifyOrderResponse body = response.getBody();
        if (body == null || body.getOrder() == null) {
            return null;
        }
        return body.getOrder().getFulfillmentStatus();
    }

    // ================== 商品发布 ==================

    /**
     * 在 Shopify 建一个商品（连同全部规格和售价）。
     *
     * <p>payload 由 Service 组装 —— Client 只管发请求和解析响应，
     * 不掺和「该传哪些字段」这类业务判断。
     *
     * <p>为什么用 REST 而不是 GraphQL：REST 一次请求就能把商品和全部变体建出来；
     * GraphQL 得先 productCreate 建商品，再 productVariantsBulkCreate 补变体，两步。
     */
    public ShopifyProductResponse.Product createProduct(Map<String, Object> payload) {

        String url = storeUrl + "/admin/api/2024-07/products.json";
        Map<String, Object> body = new HashMap<>();
        body.put("product", payload);

        ResponseEntity<ShopifyProductResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, shopifyJsonHeaders()),
                ShopifyProductResponse.class
        );

        ShopifyProductResponse result = response.getBody();
        if (result == null || result.getProduct() == null) {
            throw new IllegalStateException("Shopify 建商品成功却没有返回 product");
        }
        return result.getProduct();
    }

    /** 改已发布商品的标题、描述、上下架状态。变体价格不在这儿改，单独走 updateVariantPrice */
    public void updateProduct(Long platformProductId, Map<String, Object> payload) {

        String url = storeUrl + "/admin/api/2024-07/products/" + platformProductId + ".json";
        Map<String, Object> body = new HashMap<>();
        body.put("product", payload);

        restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(body, shopifyJsonHeaders()), String.class
        );
    }

    /**
     * 改单个变体的售价。
     *
     * <p>price 必须是字符串（"112.50"），传数字 Shopify 会报类型错误 ——
     * 这是它 REST API 的老规矩，金额一律当字符串处理，避免浮点精度问题。
     */
    public void updateVariantPrice(Long platformVariantId, String price) {

        String url = storeUrl + "/admin/api/2024-07/variants/" + platformVariantId + ".json";

        Map<String, Object> variant = new HashMap<>();
        variant.put("id", platformVariantId);
        variant.put("price", price);

        Map<String, Object> body = new HashMap<>();
        body.put("variant", variant);

        restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(body, shopifyJsonHeaders()), String.class
        );
    }

    /** 带 token 的 JSON 请求头。订单接口那几处是 GET/无 body，没有抽出来 */
    private HttpHeaders shopifyJsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
