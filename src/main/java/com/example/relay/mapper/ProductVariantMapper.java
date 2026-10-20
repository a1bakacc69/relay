package com.example.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.relay.entity.ProductVariant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * product_variants 表。
 *
 * <p>同样只用默认方法。导入时先按 supplier_variant_id 判断这个规格进过库没有，
 * 没进过就 insert —— 已存在的一律不动，因为规格上的字段
 * （cost_price 是 A 区会被同步逻辑管，sale_price 是 B 区归我们）
 * 都不该由「再点一次导入」来改。
 *
 * <p>真正的防线还是表上那条 uk_supplier_variant 唯一索引：
 * 并发下两个人同时点导入，「先查后插」会双双查空、双双插入，
 * 最后由数据库把第二条挡在外面。
 */
@Mapper
public interface ProductVariantMapper extends BaseMapper<ProductVariant> {

    /**
     * 按商品一次性重算它旗下所有规格的售价。
     *
     * <p><b>为什么不查出来再一条条 updateById：</b>
     * <ul>
     *   <li>那是 N+1 —— 3 条规格 3 次往返，30 条就 30 次</li>
     *   <li>updateById 是全量覆盖非 null 字段，会把整行盖回去，
     *       中途别处改过的列（比如同步逻辑更新的 cost_price）会被抹掉</li>
     * </ul>
     * 这条 SQL 只写 sale_price 一列，乘法交给数据库做，
     * cost_price 根本不用拉到内存。
     *
     * <p><b>update_time 必须手写。</b>实体上那个
     * {@code @TableField(update = "now()")} 只在 MyBatis-Plus 自动生成的
     * SQL 里生效，手写 SQL 它管不着 —— 漏了这句，改完价更新时间还停在旧值。
     *
     * @param productId 商品 id
     * @param ratio     加价倍数
     * @return 命中的规格条数
     */
    @Update("UPDATE product_variants " +
            "SET sale_price = cost_price * #{ratio}, update_time = now() " +
            "WHERE product_id = #{productId}")
    int repriceByProduct(@Param("productId") Long productId, @Param("ratio") BigDecimal ratio);
}
