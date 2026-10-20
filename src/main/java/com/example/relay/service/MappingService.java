package com.example.relay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.relay.common.BizException;
import com.example.relay.dto.MappingRequest;
import com.example.relay.entity.ProductMapping;
import com.example.relay.entity.ProductVariant;
import com.example.relay.mapper.ProductMappingMapper;
import com.example.relay.mapper.ProductVariantMapper;
import com.example.relay.vo.LocalVariantVO;
import com.example.relay.vo.MappingVO;
import com.example.relay.vo.UnboundVariantVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 映射关系的管理 —— 把平台上的货和我们库里的规格对起来。
 *
 * <p>为什么必须<b>人工</b>建，不能自动：店铺里那个「The Complete Snowboard」
 * 到底该由供应商的哪件货来发，机器没法判断 —— 只能靠人看一眼认出来。
 * 真实的 Dsers 也是这样，用户自己一个个绑。
 *
 * <p>所以这个模块的价值不在「自动化」，而在<b>把该干的事摆到台面上</b>：
 * unboundVariants 拿真实订单算出来「还差哪些没绑」，而不是让用户凭空猜。
 */
@Service
public class MappingService {

    @Autowired
    private ProductMappingMapper mappingMapper;

    @Autowired
    private ProductVariantMapper productVariantMapper;

    /** 已建好的映射，带两边的商品名 */
    public List<MappingVO> list() {
        return mappingMapper.selectMappingList();
    }

    /** 还差哪些没绑 —— 数据来自真实订单 */
    public List<UnboundVariantVO> unboundVariants() {
        return mappingMapper.selectUnboundVariants();
    }

    /** 我们能绑的都有哪些 —— 建映射时那个下拉框的选项 */
    public List<LocalVariantVO> localVariants() {
        return mappingMapper.selectLocalVariants();
    }

    /**
     * 建一条映射。
     *
     * @return 给用户看的一句话结果
     */
    @Transactional
    public String create(MappingRequest req) {

        if (req.getLocalVariantId() == null
                || req.getPlatform() == null
                || req.getPlatformVariantId() == null) {
            throw new BizException(400, "localVariantId、platform、platformVariantId 都不能为空");
        }

        // 本地规格得真的存在，不然绑了个空号，下单时照样找不到货
        ProductVariant variant = productVariantMapper.selectById(req.getLocalVariantId());
        if (variant == null) {
            throw new BizException(400, "本地规格不存在：" + req.getLocalVariantId());
        }

        // 这个平台变体是不是已经绑过了。
        // 表和唯一索引 uk_platform_variant 是同一个口径 —— 这里挡一道是为了给一句人话，
        // 唯一索引那道是防并发的兜底。
        Long already = mappingMapper.selectCount(
                new LambdaQueryWrapper<ProductMapping>()
                        .eq(ProductMapping::getPlatform, req.getPlatform())
                        .eq(ProductMapping::getPlatformVariantId, req.getPlatformVariantId()));

        if (already != null && already > 0) {
            throw new BizException(400, "这个平台变体已经绑过了：" + req.getPlatform()
                    + " / " + req.getPlatformVariantId() + "（要改绑先解绑）");
        }

        ProductMapping mapping = new ProductMapping();
        mapping.setLocalVariantId(req.getLocalVariantId());
        mapping.setPlatform(req.getPlatform());
        mapping.setPlatformVariantId(req.getPlatformVariantId());
        mappingMapper.insert(mapping);

        return "已绑定：" + req.getPlatform() + " 的变体 " + req.getPlatformVariantId()
                + " → 我们的规格「" + variant.getVariantTitle() + "」";
    }

    /** 解绑。之后这个平台变体再出现在订单里，下单时会报「还没映射」 */
    @Transactional
    public String delete(Long id) {

        ProductMapping mapping = mappingMapper.selectById(id);
        if (mapping == null) {
            throw new BizException(400, "映射不存在：" + id);
        }

        mappingMapper.deleteById(id);

        return "已解绑：" + mapping.getPlatform() + " / " + mapping.getPlatformVariantId();
    }
}
