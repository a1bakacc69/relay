package com.example.relay.Controller;

import com.example.relay.common.R;
import com.example.relay.dto.ProductUpdateRequest;
import com.example.relay.entity.Product;
import com.example.relay.service.ProductImportService;
import com.example.relay.service.ProductPublishService;
import com.example.relay.service.ProductService;
import com.example.relay.vo.ProductVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品模块的入口。
 *
 * <p>五个接口，正好是商品在我们这边的一生：
 * <ol>
 *   <li>import  —— 从供应商那儿拿过来（拿完是草稿）</li>
 *   <li>list / get —— 看看导了些什么</li>
 *   <li>update  —— 改成我们想卖的样子（改标题、换图）</li>
 *   <li>pricing —— 定价，草稿变成在售</li>
 * </ol>
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductImportService productImportService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductPublishService productPublishService;

    /**
     * 从供应商导一个商品进来（含全部规格）。
     *
     * <p>POST http://localhost:8080/products/import?supplierProductId=2
     *
     * <p>重复调同一个 id 是安全的 —— 第二次什么都不做。
     */
    @PostMapping("/import")
    public R<String> importProduct(@RequestParam Long supplierProductId) {
        return R.ok(productImportService.importProduct(supplierProductId));
    }

    /** 商品列表。GET http://localhost:8080/products/list */
    @GetMapping("/list")
    public R<List<Product>> list() {
        return R.ok(productService.list());
    }

    /** 商品详情，带全部规格。GET http://localhost:8080/products/1 */
    @GetMapping("/{id}")
    public R<ProductVO> getById(@PathVariable Long id) {
        return R.ok(productService.getById(id));
    }

    /** 改标题、描述、图片、类目、状态。给什么改什么，没给的保持原样 */
    @PutMapping("/{id}")
    public R<String> update(@PathVariable Long id, @RequestBody ProductUpdateRequest req) {
        productService.update(id, req);
        return R.ok("已保存");
    }

    /**
     * 定价。
     *
     * <p>PUT http://localhost:8080/products/1/pricing?priceRatio=2.5
     *
     * <p>按这个倍数把这个商品下所有规格的售价重算一遍；
     * 如果已经发布过，顺手把新价推到平台上 —— 不然库里价改了、店里还是旧价。
     */
    @PutMapping("/{id}/pricing")
    public R<String> updatePricing(@PathVariable Long id, @RequestParam BigDecimal priceRatio) {
        // 先算价落库（事务内），再推平台。顺序不能反：平台一旦收下就撤不回来，
        // 反过来才会造出「平台改了、本地没改」的真不一致
        String local = productService.updatePricing(id, priceRatio);
        return R.ok(productPublishService.syncPricesToPlatform(id, local));
    }

    /**
     * 把本地商品发布到 Shopify。
     *
     * <p>POST http://localhost:8080/products/1/publish
     *
     * <p>第一次调用创建商品并自动建映射，之后再调用就是更新标题、描述、售价。
     */
    @PostMapping("/{id}/publish")
    public R<String> publish(@PathVariable Long id) {
        return R.ok(productPublishService.publish(id));
    }
}
