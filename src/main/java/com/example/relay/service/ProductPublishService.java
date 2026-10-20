package com.example.relay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.relay.client.ShopifyClient;
import com.example.relay.common.BizException;
import com.example.relay.dto.ShopifyProductResponse;
import com.example.relay.entity.Product;
import com.example.relay.entity.ProductMapping;
import com.example.relay.entity.ProductVariant;
import com.example.relay.enums.ProductStatus;
import com.example.relay.mapper.ProductMapper;
import com.example.relay.mapper.ProductMappingMapper;
import com.example.relay.mapper.ProductVariantMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把本地商品发布到 Shopify —— 商品模块的最后一步。
 *
 * <p>导入是「从供应商拿过来」，发布是「放到平台上去卖」，方向正好相反。
 * 发布完拿到的变体 id 会顺手写进 product_mappings，
 * 于是「平台变体 → 本地规格 → 供应商变体」这条链就自动接上了，
 * 不用再人工建映射。
 *
 * <p><b>为什么用 products.platform_product_id 判断新建还是更新：</b>
 * 有值说明这个商品已经在 Shopify 上存在了，再点发布只能走更新；
 * 否则重复点几次就会在店铺里堆出好几个同名商品。
 *
 * <p><b>为什么不加 @Transactional：</b>这里夹着两次网络调用（建商品、改变体价）。
 * Shopify 那边的写入一旦成功就撤不回来，回滚本地事务并不能让它们一起回退，
 * 反而会把数据库连接白白占住几十秒 —— Aiven 免费档的连接数很紧张。
 * 所以这里靠「先写 Shopify、后写本地」的顺序 + platform_product_id 做补偿判断：
 * 本地写失败时重试一次，会走更新路径，不会重复建商品。
 */
@Service
public class ProductPublishService {

    private static final String PLATFORM_SHOPIFY = "SHOPIFY";

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductVariantMapper productVariantMapper;

    @Autowired
    private ProductMappingMapper productMappingMapper;

    @Autowired
    private ShopifyClient shopifyClient;

    public String publish(Long productId) {

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(400, "商品不存在：" + productId);
        }

        List<ProductVariant> variants = productVariantMapper.selectList(
                new LambdaQueryWrapper<ProductVariant>()
                        .eq(ProductVariant::getProductId, productId)
                        .orderByAsc(ProductVariant::getId));

        if (variants.isEmpty()) {
            throw new BizException(400, "商品没有规格，无法发布");
        }

        // 售价没算过就别往平台上推 —— Shopify 那边会以 400 打回来，
        // 但那个错误信息是一大串 JSON，不如在这儿说清楚该先调哪个接口
        for (ProductVariant variant : variants) {
            if (variant.getSalePrice() == null) {
                throw new BizException(400,
                        "规格「" + variant.getVariantTitle() + "」还没定价，"
                                + "先调 PUT /products/" + productId + "/pricing");
            }
        }

        if (product.getPlatformProductId() == null) {
            return createOnShopify(product, variants);
        }
        return updateOnShopify(product, variants);
    }

    // -------------------- 首次发布：建商品 + 自动建映射 --------------------

    private String createOnShopify(Product product, List<ProductVariant> variants) {

        ShopifyProductResponse.Product created;
        try {
            created = shopifyClient.createProduct(buildCreatePayload(product, variants));
        } catch (HttpStatusCodeException e) {
            throw new BizException(500,
                    "Shopify 建商品失败：" + e.getResponseBodyAsString());
        }

        // Shopify 返回的 id 必须立刻落库 —— 它是下次「更新」而不是「再建一个」的唯一依据
        product.setPlatformProductId(created.getId());
        productMapper.updateById(product);

        int mapped = bindMappings(variants, created.getVariants());

        return "已发布到 Shopify（商品 id=" + created.getId() + "），"
                + "自动绑定 " + mapped + " 条映射";
    }

    /**
     * 把 Shopify 返回的变体 id 写进映射表。
     *
     * <p>匹配靠 title：Shopify 对单选项商品，变体 title 就等于传过去的 option1，
     * 也就是本地的 variantTitle。不靠返回顺序 —— 那个顺序 API 没承诺过。
     *
     * <p><b>按 (platform, local_variant_id) 判重，不按 platform_variant_id：</b>
     * 后者会变 —— 商品在 Shopify 删掉重建，平台变体 id 就换了一批，
     * 按平台 id 查永远查不到，同一个规格会被插出第二条映射，订单进来命中哪条看运气。
     * 所以这里统一走 upsert：已绑过就把平台变体 id 改过去，没绑过才新增。
     */
    private int bindMappings(List<ProductVariant> locals,
                             List<ShopifyProductResponse.Variant> remotes) {

        if (remotes == null) {
            return 0;
        }

        int mapped = 0;
        for (ProductVariant local : locals) {

            ShopifyProductResponse.Variant matched = null;
            for (ShopifyProductResponse.Variant remote : remotes) {
                if (local.getVariantTitle().equals(remote.getTitle())) {
                    matched = remote;
                    break;
                }
            }
            if (matched == null) {
                continue;
            }

            // 判断「绑过没有」必须看 (platform, local_variant_id)，不能看 platform_variant_id：
            // 商品在 Shopify 被删掉后重建，平台变体 id 会换一批，按平台 id 查永远查不到，
            // 同一个规格就会被插出第二条映射 —— 订单进来命中哪条看运气。
            ProductMapping exists = findMapping(local.getId());
            if (exists != null) {
                // 平台变体 id 变了（商品重建过）就把它改到新 id 上，不新增一行
                if (!matched.getId().equals(exists.getPlatformVariantId())) {
                    exists.setPlatformVariantId(matched.getId());
                    productMappingMapper.updateById(exists);
                    mapped++;
                }
                continue;
            }

            ProductMapping mapping = new ProductMapping();
            mapping.setLocalVariantId(local.getId());
            mapping.setPlatform(PLATFORM_SHOPIFY);
            mapping.setPlatformVariantId(matched.getId());
            productMappingMapper.insert(mapping);
            mapped++;
        }
        return mapped;
    }

    // -------------------- 再次发布：改标题/描述/状态 + 同步变体价 --------------------

    private String updateOnShopify(Product product, List<ProductVariant> variants) {

        try {
            shopifyClient.updateProduct(product.getPlatformProductId(), buildUpdatePayload(product));

            int repriced = pushVariantPrices(variants);

            return "已更新 Shopify 商品（id=" + product.getPlatformProductId() + "），"
                    + "同步 " + repriced + " 个规格的价格";

        } catch (HttpStatusCodeException e) {
            throw new BizException(500,
                    "Shopify 更新商品失败：" + e.getResponseBodyAsString());
        }
    }

    /**
     * 把本地售价推到已经绑定的平台变体上。
     *
     * <p>抽出来是因为两个入口都要用：再次发布，以及只改定价没点发布。
     * 后者原来会留下「库里价改了、店里还是旧价」的口子。
     *
     * <p>price 传字符串：Shopify 的金额一律按字符串收，传数字会报类型错误。
     */
    private int pushVariantPrices(List<ProductVariant> variants) {

        int repriced = 0;
        for (ProductVariant variant : variants) {
            ProductMapping mapping = findMapping(variant.getId());
            if (mapping == null) {
                // 正常不会走到这儿：首次发布时每个规格都建了映射。
                // 真出现了说明有人手工删过映射，跳过即可，不阻断整体
                continue;
            }
            shopifyClient.updateVariantPrice(
                    mapping.getPlatformVariantId(),
                    variant.getSalePrice().toPlainString());
            repriced++;
        }
        return repriced;
    }

    /**
     * 定价改完之后同步到平台，返回一句能直接给用户看的话。
     *
     * <p>顺序是「先本地、后平台」：平台一旦收下就撤不回来，
     * 反过来先推平台再写本地，本地失败才会造出真正的不一致。
     * 代价是平台这步失败时本地售价已经变了 —— 所以错误信息必须点明这一点，
     * 让人知道该重试哪个动作，而不是以为整件事没发生。
     */
    public String syncPricesToPlatform(Long productId, String doneMessage) {

        Product product = productMapper.selectById(productId);
        if (product == null || product.getPlatformProductId() == null) {
            return doneMessage + "（还没发布到平台，平台上价格不变）";
        }

        List<ProductVariant> variants = productVariantMapper.selectList(
                new LambdaQueryWrapper<ProductVariant>()
                        .eq(ProductVariant::getProductId, productId)
                        .orderByAsc(ProductVariant::getId));

        try {
            int pushed = pushVariantPrices(variants);
            return doneMessage + "，已同步 " + pushed + " 个规格到 Shopify";
        } catch (HttpStatusCodeException e) {
            throw new BizException(500,
                    doneMessage + "；但推送到 Shopify 失败，平台价格仍是旧的："
                            + e.getResponseBodyAsString());
        }
    }

    /**
     * 查这条本地规格在 Shopify 上的对应变体。
     *
     * <p>用 selectList 取第一条而不是 selectOne —— local_variant_id 上没有唯一约束
     * （同一个货源允许同时上架多个平台），selectOne 碰到多行会直接抛异常。
     * 这里限定 platform = SHOPIFY，正常只有一条。
     */
    private ProductMapping findMapping(Long localVariantId) {
        List<ProductMapping> list = productMappingMapper.selectList(
                new LambdaQueryWrapper<ProductMapping>()
                        .eq(ProductMapping::getLocalVariantId, localVariantId)
                        .eq(ProductMapping::getPlatform, PLATFORM_SHOPIFY));
        return list.isEmpty() ? null : list.get(0);
    }

    // -------------------- 请求体组装 --------------------

    private Map<String, Object> buildCreatePayload(Product product, List<ProductVariant> variants) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", product.getTitle());
        putIfNotNull(payload, "body_html", product.getDescription());
        payload.put("vendor", "Relay");
        payload.put("status", toShopifyStatus(product.getStatus()));

        // 只存外链，Shopify 会自己把图抓回去。「拿不到图」不会让建商品失败，只是没图
        if (product.getImageUrl() != null) {
            payload.put("images", List.of(Map.of("src", product.getImageUrl())));
        }

        // 多个规格必须先声明 option，否则 Shopify 只认一个变体。
        // 名字叫 Size 而不是「容量」：选项名是自由文本，用通用词更保险
        List<String> optionValues = variants.stream()
                .map(ProductVariant::getVariantTitle)
                .toList();
        payload.put("options", List.of(Map.of("name", "Size", "values", optionValues)));

        List<Map<String, Object>> variantPayloads = new ArrayList<>();
        for (ProductVariant variant : variants) {
            Map<String, Object> item = new HashMap<>();
            item.put("option1", variant.getVariantTitle());
            // 金额传字符串，传数字 Shopify 会报类型错误
            item.put("price", variant.getSalePrice().toPlainString());
            putIfNotNull(item, "sku", variant.getSku());
            variantPayloads.add(item);
        }
        payload.put("variants", variantPayloads);

        return payload;
    }

    /** 更新只带商品级的字段。变体价格走单独的 variants 接口，见 updateOnShopify */
    private Map<String, Object> buildUpdatePayload(Product product) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("id", product.getPlatformProductId());
        payload.put("title", product.getTitle());
        putIfNotNull(payload, "body_html", product.getDescription());
        payload.put("status", toShopifyStatus(product.getStatus()));
        return payload;
    }

    /** 本地 ON_SALE 对应 Shopify 的 active，其余（DRAFT / OFF_SHELF）一律 draft */
    private String toShopifyStatus(String localStatus) {
        return ProductStatus.ON_SALE.name().equals(localStatus) ? "active" : "draft";
    }

    /** null 值不塞进 JSON —— Shopify 收到 "body_html": null 和完全不传，行为不一样 */
    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
