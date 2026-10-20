package com.example.relay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.relay.common.BizException;
import com.example.relay.dto.ProductUpdateRequest;
import com.example.relay.entity.Product;
import com.example.relay.entity.ProductVariant;
import com.example.relay.enums.ProductStatus;
import com.example.relay.mapper.ProductMapper;
import com.example.relay.mapper.ProductVariantMapper;
import com.example.relay.vo.ProductVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品的查询与加工 —— 导入之后的活都在这儿。
 *
 * <p>和 ProductImportService 的分工很干脆：
 * 那边管「从供应商那儿拿过来」，这边管「拿过来之后我们怎么卖」。
 */
@Service
public class ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductVariantMapper productVariantMapper;

    /** 商品列表。不带规格 —— 列表轻、详情重，要规格去查详情 */
    public List<Product> list() {
        return productMapper.selectList(
                new LambdaQueryWrapper<Product>().orderByDesc(Product::getId));
    }

    /** 商品详情，带全部规格 */
    public ProductVO getById(Long id) {

        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(400, "商品不存在：" + id);
        }

        List<ProductVariant> variants = productVariantMapper.selectList(
                new LambdaQueryWrapper<ProductVariant>()
                        .eq(ProductVariant::getProductId, id)
                        .orderByAsc(ProductVariant::getId));

        // 字段名两边对得上，直接拷；拷完再补上实体里没有的规格数组
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(product, vo);
        vo.setVariants(variants);

        return vo;
    }

    /**
     * 改商品本身的营销信息。
     *
     * <p>只动请求里给了的字段 —— 没传的就留在原样。
     * 加价倍数不在这儿改，它一改就得连带重算所有规格的售价，单独走 updatePricing。
     */
    @Transactional
    public void update(Long id, ProductUpdateRequest req) {

        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(400, "商品不存在：" + id);
        }

        if (req.getTitle() != null) {
            product.setTitle(req.getTitle());
        }
        if (req.getDescription() != null) {
            product.setDescription(req.getDescription());
        }
        if (req.getImageUrl() != null) {
            product.setImageUrl(req.getImageUrl());
        }
        if (req.getCategory() != null) {
            product.setCategory(req.getCategory());
        }

        if (req.getStatus() != null) {
            // 先试一下这个值能不能对上枚举，对不上就挡住 —— 不然脏数据就进库了
            try {
                ProductStatus.valueOf(req.getStatus());
            } catch (IllegalArgumentException e) {
                throw new BizException(400,
                        "状态只能是 DRAFT / ON_SALE / OFF_SHELF，收到：" + req.getStatus());
            }
            product.setStatus(req.getStatus());
        }

        productMapper.updateById(product);
    }

    /**
     * 改加价倍数，并重算这个商品下所有规格的售价。
     *
     * <p>售价 = 采购价 × 加价倍数，逐条算完存进 sale_price 这一列。
     * <b>存下来而不是每次查的时候现算</b> —— 因为供应商将来调了采购价，
     * 历史订单里那个售价不能被改写。数字当时是多少就是多少。
     *
     * @param priceRatio 加价倍数，必须大于 0。2.00 表示卖两倍价
     */
    @Transactional
    public String updatePricing(Long id, BigDecimal priceRatio) {

        if (priceRatio == null || priceRatio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "加价倍数必须大于 0，收到：" + priceRatio);
        }

        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(400, "商品不存在：" + id);
        }

        product.setPriceRatio(priceRatio);

        // 定完价它就不是「已导入未定价」的草稿了。
        // 但已下架的不碰 —— 改个价不该把下架的商品悄悄复上架。
        if (ProductStatus.DRAFT.name().equals(product.getStatus())) {
            product.setStatus(ProductStatus.ON_SALE.name());
        }

        productMapper.updateById(product);

        // 重算这个商品旗下所有规格的售价 —— 一条 SQL 交给数据库，不查出来再逐条改。
        // cost_price 是供应商的，我们只拿它乘，从不改它
        int repriced = productVariantMapper.repriceByProduct(id, priceRatio);

        return "已按 " + priceRatio + " 倍重算 " + repriced + " 条规格的售价";
    }
}
