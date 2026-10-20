package com.example.relay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.relay.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * products 表。
 *
 * <p>目前不需要自定义 SQL —— 导入时两个动作用默认方法就够了：
 * <ol>
 *   <li>按 supplier_product_id 查有没有导过（LambdaQueryWrapper）</li>
 *   <li>没导过 → insert；导过 → <b>什么都不做</b>，直接返回</li>
 * </ol>
 *
 * <p>注意第 2 条不是偷懒：商品一旦导进来，标题、加价倍数、上下架就都归我们管了，
 * 再点一次导入不该把它们冲回供应商那边的原样。这是「导入只管第一次」这条规矩
 * 在代码里的落点（详见 ProductImportService）。
 *
 * <p>updateById 只在改商品、改价这两个接口里用，
 * 而且传进去的是 <b>先查出来的那一份</b> 再改几个字段 —— 不是拿请求体直接覆盖，
 * 所以客户端没法通过这两个接口碰到 supplier_product_id 这类身份字段。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
