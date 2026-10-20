package com.example.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.relay.entity.ProductMapping;
import com.example.relay.vo.LocalVariantVO;
import com.example.relay.vo.MappingVO;
import com.example.relay.vo.UnboundVariantVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * product_mappings 表。
 *
 * <p>增删改查用默认方法就够，但两个查询要 join 别的表，所以写了 SQL。
 */
@Mapper
public interface ProductMappingMapper extends BaseMapper<ProductMapping> {

    /**
     * 映射列表，带上两边的商品名 —— 光两个 ID 谁也认不出是哪件货。
     *
     * <p>注意这里顺着 localVariantId 又查了一层，把 supplierVariantId 带出来了：
     * 这就是「映射表不冗余存供应商变体ID」时，第二跳长什么样。
     */
    @Select("""
            SELECT m.id                  AS id,
                   m.local_variant_id    AS localVariantId,
                   m.platform            AS platform,
                   m.platform_variant_id AS platformVariantId,
                   p.title               AS productTitle,
                   v.variant_title       AS variantTitle,
                   v.supplier_variant_id AS supplierVariantId
              FROM product_mappings m
              JOIN product_variants v ON v.id = m.local_variant_id
              JOIN products p         ON p.id = v.product_id
             ORDER BY m.id DESC
            """)
    List<MappingVO> selectMappingList();

    /**
     * 出现在订单里、但还没建映射的变体。
     *
     * <p>把「出现在订单里」和「还没映射」放在一条 SQL 里做，靠的是 NOT EXISTS 子查询 ——
     * 订单明细一条条去比对映射表，比拉回内存里做差集更省事，也不会漏掉平台维度
     * （同一个 variant_id 在不同平台上是两回事）。
     */
    @Select("""
            SELECT oi.variant_id    AS variantId,
                   o.platform       AS platform,
                   oi.title         AS title,
                   oi.variant_title AS variantTitle,
                   COUNT(*)         AS occurrences
              FROM order_items oi
              JOIN orders o ON o.id = oi.order_id
             WHERE oi.variant_id IS NOT NULL
               AND NOT EXISTS (
                   SELECT 1
                     FROM product_mappings m
                    WHERE m.platform = o.platform
                      AND m.platform_variant_id = oi.variant_id
               )
             GROUP BY oi.variant_id, o.platform, oi.title, oi.variant_title
             ORDER BY occurrences DESC
            """)
    List<UnboundVariantVO> selectUnboundVariants();

    /**
     * 我们自己的全部规格，带商品名。
     *
     * <p>给「建映射」那个下拉框当选项用。左边的 unbound 是「还差什么」，
     * 这个是「能绑什么」—— 一个拾起问题，一个给出答案。
     */
    @Select("""
            SELECT v.id                  AS id,
                   p.title               AS productTitle,
                   v.variant_title       AS variantTitle,
                   v.cost_price          AS costPrice,
                   v.sale_price          AS salePrice,
                   v.supplier_variant_id AS supplierVariantId
              FROM product_variants v
              JOIN products p ON p.id = v.product_id
             ORDER BY p.id DESC, v.id ASC
            """)
    List<LocalVariantVO> selectLocalVariants();
}
