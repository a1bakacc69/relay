package com.example.relay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.relay.dto.SupplierProductDto;
import com.example.relay.entity.Product;
import com.example.relay.entity.ProductVariant;
import com.example.relay.enums.ProductStatus;
import com.example.relay.mapper.ProductMapper;
import com.example.relay.mapper.ProductVariantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品导入的「写库那一半」。
 *
 * <p><b>单独一个 Bean，是为了让 @Transactional 真的生效。</b>
 * 拆出去的理由是事务里不能夹网络调用：@Transactional 一贴上，进方法就跟连接池
 * 要一条数据库连接，一直攥到方法结束。要是发 HTTP 那步也在里面，供应商慢一次
 * （读超时配的是 30 秒），这条连接就白占 30 秒 —— 池子总共十来条，几个请求一挤
 * 就满，接着整个服务里要数据库的接口全排队。所以「发网络请求」和「写数据库」
 * 必须分家。
 *
 * <p><b>为什么不就写在 ProductImportService 里、拆成两个方法互相调：</b>
 * 那样是自调用（this.xxx()），不走 Spring 代理，@Transactional 会
 * <b>静默失效</b> —— 不报错、日志里也看不出来，以为有事务其实没有。
 *
 * <p>走到这里的数据已经过了外层三关（取到了、没导过、有规格），本类只管写，
 * 不再做业务判断。
 */
@Slf4j
@Service
public class ProductImportTxService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductVariantMapper productVariantMapper;

    /**
     * 把供应商的一份商品数据落成本地商品 + 全部规格。
     *
     * <p>全程在一个事务里：商品和规格要么都进，要么都不进。中途任何一步抛异常，
     * 已经执行的 insert 会被整体撤销，不用手动清理 —— 手动 delete 也在这个事务里，
     * 会被一起回滚，等于没写。
     *
     * <p>注意回滚能生效的前提：抛出的必须是 RuntimeException
     * （我们的 BizException 正是），受检异常 Spring 默认不回滚。
     *
     * @param sp 供应商那边取回来的商品详情，必定非 null 且规格非空
     * @return 给用户看的一句话结果
     */
    @Transactional
    public String persist(SupplierProductDto.Data sp) {

        // ④ 先把商品插进来，拿到我们自己的 id
        Product product = new Product();
        product.setSupplierProductId(sp.getId());

        // 标题、描述、图片导进来之后就是我们的了 —— 改不改由我们，人家管不着
        product.setTitle(sp.getTitle());
        product.setDescription(sp.getDescription());
        product.setImageUrl(sp.getImageUrl());
        product.setCategory(sp.getCategory());

        // 状态是 DRAFT，不是供应商那个 ON_SALE。
        // 两边同名不同义：他在说他家还卖不卖，我们在说我们这边定没定价。
        product.setStatus(ProductStatus.DRAFT.name());

        // price_ratio 故意不设：NULL 就表示「还没定价」，每条规格的售价也就无从算起。
        // 等调了改价接口，比例有了，售价才一起算出来。
        productMapper.insert(product);

        // ⑤ 规格层：一条条搬。已经存在的不动
        //   外层已经把关，走到这里 variants 必定非空，不用再判一次
        int inserted = 0;
        int skipped = 0;

        for (SupplierProductDto.Variant sv : sp.getVariants()) {

            // 先查后插。正常流程下这里必然查不到（商品都是新插的），
            // 留着是防「上次导到一半挂了、商品插进去规格没插完」这类脏数据
            Long already = productVariantMapper.selectCount(
                    new LambdaQueryWrapper<ProductVariant>()
                            .eq(ProductVariant::getSupplierVariantId, sv.getId()));
            if (already != null && already > 0) {
                skipped++;
                continue;
            }

            ProductVariant v = new ProductVariant();
            v.setProductId(product.getId());

            // ★ 这一根线最关键：下单给供应商时传的就是它
            v.setSupplierVariantId(sv.getId());

            v.setVariantTitle(sv.getVariantTitle());
            v.setSku(sv.getSku());
            v.setImageUrl(sv.getImageUrl());

            // A 区：采购价，供应商说了算，原样搬过来
            v.setCostPrice(sv.getPrice());

            // salePrice 留空 —— 还没定价。
            // stock 干脆没接 —— 库存时刻在变又只归供应商管，存一份维护不了的就是放一句谎话。
            productVariantMapper.insert(v);
            inserted++;
        }

        log.info("导入商品成功：supplierProductId={}，本地 id={}，新增规格 {} 条，跳过 {} 条",
                sp.getId(), product.getId(), inserted, skipped);

        return "导入成功：商品「" + product.getTitle() + "」（本地 id=" + product.getId()
                + "），新增 " + inserted + " 个规格。现在还是草稿，定了价才会上架";
    }
}
