package com.example.relay.client;

import com.example.relay.common.BizException;
import com.example.relay.dto.SupplierOrderQueryResponse;
import com.example.relay.dto.SupplierOrderRequest;
import com.example.relay.dto.SupplierOrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 负责跟供货商平台打交道：把订单发过去，拿回对方的订单号。
 * 只发请求、只取单号，不碰数据库 —— 写库是调它的 Service 的事。
 */
@Component
public class SupplierClient {

    @Value("${supplier.base.url}")
    private String baseUrl;

    /**
     * ★ 必须带超时。
     *
     * <p>RestTemplate 默认<b>没有超时</b> —— 对方要是挂着不回应（服务没起、网络断了、
     * 容器冷启动），这个线程就永远卡在这儿。而且是卡在 HTTP 调用里，
     * 数据库连接还开着，几个请求就能把连接池和线程池耗干，
     * 连着你自己一起拖死。
     */
    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 连不上就赶紧认输。5 秒足够判断「对面活着吗」
        factory.setConnectTimeout(Duration.ofSeconds(5));

        // 连上了但对方处理得慢，给它多留点时间。
        // 部署到免费云之后冷启动可能要几十秒，这个值得放宽一些
        factory.setReadTimeout(Duration.ofSeconds(30));

        return new RestTemplate(factory);
    }

    public String placeOrder(SupplierOrderRequest req) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<SupplierOrderRequest> entity = new HttpEntity<>(req, headers);

        ResponseEntity<SupplierOrderResponse> response = restTemplate.exchange(
                baseUrl + "/supplier/orders",
                HttpMethod.POST,
                entity,
                SupplierOrderResponse.class
        );

        SupplierOrderResponse body = response.getBody();
        if (body == null) {
            throw new BizException(500, "供应商返回了空响应");
        }

        assertSuccess(body.getCode(), body.getMsg());

        if (body.getData() == null || body.getData().getSupplierOrderId() == null) {
            throw new BizException(500, "供应商说成功了，但没带订单号");
        }

        return body.getData().getSupplierOrderId();
    }

    // 查供货商那边的发货状态
    public SupplierOrderQueryResponse.Data queryOrder(String supplierOrderId) {

        String url = baseUrl + "/supplier/orders/" + supplierOrderId;

        ResponseEntity<SupplierOrderQueryResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, null, SupplierOrderQueryResponse.class
        );

        SupplierOrderQueryResponse body = response.getBody();
        if (body == null) {
            throw new BizException(500, "供应商返回了空响应");
        }

        assertSuccess(body.getCode(), body.getMsg());

        if (body.getData() == null) {
            throw new BizException(500, "供应商说成功了，但没带订单信息");
        }

        return body.getData();
    }

    /**
     * R 契约里，HTTP 200 只说明「送到了」，不代表「办成了」。
     *
     * <p>对方的业务错误（库存不足、规格不存在）走的是 code 字段，HTTP 状态码照样是 200。
     * 不检查它就等于默认「每次都成功」，拿着 data=null 往下走，
     * 最后炸成一句「服务器内部错误」—— 真正的线索（库存不足）就这么被盖掉了，
     * 用户只看到「系统坏了」，永远不知道是自己该去补货。
     *
     * <p>所以把对方的 code 和 msg 原样转达出去：这类错误用户自己能解决，
     * 不该和「供应商服务挂了」那种系统故障混为一谈。
     */
    private static void assertSuccess(Integer code, String msg) {
        if (code == null) {
            throw new BizException(500, "供应商没返回 code，响应格式对不上");
        }
        if (code != 200) {
            throw new BizException(code, msg);
        }
    }

}
