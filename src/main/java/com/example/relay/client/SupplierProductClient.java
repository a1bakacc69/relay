package com.example.relay.client;

import com.example.relay.dto.SupplierProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 商品目录的客户端 —— 从供应商平台「拉」货。
 *
 * <p>和 SupplierClient 正好相反，方向别搞混：
 * <ul>
 *   <li>{@link SupplierClient} —— <b>我们推</b>数据给供应商（下单、发货）</li>
 *   <li>本类 —— <b>我们拉</b>供应商的数据（浏览、导入）</li>
 * </ul>
 *
 * <p>供应商那边商品接口有两个：列表和详情。
 * 这里只封装<b>详情</b> —— 列表是给前端挑货页用的，等真要做那个页面时再加，
 * 现在写上没人调，只是提前欠债。
 *
 * <p>只发请求、转化成我们自己的对象，不碰数据库 —— 写库是 ProductImportService 的事。
 */
@Component
public class SupplierProductClient {

    @Value("${supplier.base.url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 拉一个商品的完整信息，含全部规格。
     *
     * <p>导入就得调它。列表接口只给「够挑货」的摘要（标题、图、价格区间），
     * 没有规格 —— 而规格恰恰是后面建对应关系时最关键的东西，缺了就没法下单。
     *
     * @param supplierProductId 供应商侧的 supplier_products.id
     * @return 商品详情；供应商那边没有这个 id 时返回 null
     */
    public SupplierProductDto.Data fetchDetail(Long supplierProductId) {

        String url = baseUrl + "/supplier/products/" + supplierProductId;

        ResponseEntity<SupplierProductDto> response = restTemplate.exchange(
                url, HttpMethod.GET, null, SupplierProductDto.class
        );

        SupplierProductDto body = response.getBody();

        // 供应商那边商品不存在时不会报 4xx，而是正常返回 {"code":404,"msg":"商品不存在：999"}
        // 所以不能只看 HTTP 状态，得自己看一眼业务码
        if (body == null || body.getCode() == null || body.getCode() != 200) {
            return null;
        }

        return body.getData();
    }
}
