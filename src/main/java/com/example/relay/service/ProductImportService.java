package com.example.relay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.relay.client.SupplierProductClient;
import com.example.relay.common.BizException;
import com.example.relay.dto.SupplierProductDto;
import com.example.relay.entity.Product;
import com.example.relay.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 商品导入 —— 把供应商的货搬成我们自己的商品。
 *
 * <p><b>导入只负责「第一次」。</b>
 * 一个供应商商品导进来之后，它的标题、加价倍数、上下架就都归我们管了，
 * 再点一次导入<b>什么都不会发生</b> —— 不覆盖、不新增。
 * 供应商那边后来的改动走「商品同步」那条线（后面做），不走导入。
 *
 * <p>为什么这么分：导入和同步虽然都是「把供应商的数据弄过来」，
 * 但一个是我们主动去挑货（挑完就加工），一个是人家推着通知我们变了。
 * 混在一起的话，每次供应商改个标题，我们自己改的标题就被冲回去了。
 *
 * <p><b>这个类不带事务，是故意的。</b>
 * 它只做两件事：发 HTTP 把货取回来、判断这批货该不该导。真正的写库在
 * {@link ProductImportTxService}。理由见那个类的注释，一句话：
 * 事务里不能夹网络调用，否则一条数据库连接会被白占在一段网络等待上。
 */
@Service
public class ProductImportService {

    @Autowired
    private SupplierProductClient supplierProductClient;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductImportTxService productImportTxService;

    /**
     * 从供应商导入一个商品（连同它的全部规格）。
     *
     * <p>三关都过了才开事务：① 供应商那边真有这个货 ② 本地没导过 ③ 至少有一个规格。
     * 不合格的连事务都不开 —— 少占一条数据库连接。
     *
     * @param supplierProductId 供应商侧的 supplier_products.id
     * @return 给用户看的一句话结果
     */
    public String importProduct(Long supplierProductId) {

        // ① 去供应商那边取货 —— 真的发 HTTP 到 8081，不是 mock
        //    这步在事务外面。它是个「读」，失败了本地什么都没变，天然安全
        SupplierProductDto.Data sp = supplierProductClient.fetchDetail(supplierProductId);
        if (sp == null) {
            throw new BizException(400, "供应商平台没有这个商品：" + supplierProductId);
        }

        // ② 商品层：导过就到此为止
        Product existed = productMapper.selectOne(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getSupplierProductId, supplierProductId));

        if (existed != null) {
            return "这个商品之前导入过（本地 id=" + existed.getId()
                    + "），重复导入不做任何事。供应商那边后改的内容，要等商品同步功能上线才会过来";
        }

        // ③ 一个规格都没有的商品，不许导
        //   规格是后面建映射的唯一抓手（product_mappings 绑的是变体），
        //   商品没有规格 → 建不了映射 → 订单永远下不出去。
        //   所以这里不能放过：放过就等于插进去一个永远接不进闭环的摆设，
        //   还回头跟用户说一句「导入成功」。
        if (sp.getVariants() == null || sp.getVariants().isEmpty()) {
            throw new BizException(400, "商品「" + sp.getTitle()
                    + "」在供应商那边没有任何规格，导进来也建不了映射，先让供应商把规格补上");
        }

        // ④⑤ 写库 —— 交给带事务的那个 Bean。
        //   注意必须走注入调用，不能把 persist 挪进本类再用 this 调：
        //   自调用不走 Spring 代理，@Transactional 会静默失效。
        return productImportTxService.persist(sp);
    }
}
