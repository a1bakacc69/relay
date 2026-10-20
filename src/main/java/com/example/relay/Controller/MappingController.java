package com.example.relay.Controller;

import com.example.relay.common.R;
import com.example.relay.dto.MappingRequest;
import com.example.relay.service.MappingService;
import com.example.relay.vo.LocalVariantVO;
import com.example.relay.vo.MappingVO;
import com.example.relay.vo.UnboundVariantVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 映射管理的入口。
 *
 * <p>四个接口是一条完整的工作流：
 * <ol>
 *   <li>unbound-variants —— 我还差哪些没绑？（数据来自真实订单，不用猜）</li>
 *   <li>POST —— 挑一个绑上</li>
 *   <li>list —— 现在都绑成什么样了</li>
 *   <li>DELETE —— 绑错了、换货源了，解掉</li>
 * </ol>
 */
@RestController
@RequestMapping("/mappings")
public class MappingController {

    @Autowired
    private MappingService mappingService;

    /** 已建好的映射。GET http://localhost:8080/mappings/list */
    @GetMapping("/list")
    public R<List<MappingVO>> list() {
        return R.ok(mappingService.list());
    }

    /**
     * 还差哪些没绑。
     *
     * <p>GET http://localhost:8080/mappings/unbound-variants
     *
     * <p>结果直接来自订单表 —— 没被买过的商品不会出现在这里，
     * 也就意味着「先绑最要紧的」这件事有了依据：按出现次数排。
     */
    @GetMapping("/unbound-variants")
    public R<List<UnboundVariantVO>> unboundVariants() {
        return R.ok(mappingService.unboundVariants());
    }

    /**
     * 我们有哪几条规格可以绑。
     *
     * <p>GET http://localhost:8080/mappings/local-variants
     *
     * <p>和 unbound-variants 是一对：一个说「还差什么」，一个说「能拿什么去对」。
     * 建映射的弹窗同时要用到这两份数据。
     */
    @GetMapping("/local-variants")
    public R<List<LocalVariantVO>> localVariants() {
        return R.ok(mappingService.localVariants());
    }

    /**
     * 建立绑定。
     *
     * <p>POST http://localhost:8080/mappings
     * <pre>{"localVariantId":1,"platform":"SHOPIFY","platformVariantId":888888880001}</pre>
     */
    @PostMapping
    public R<String> create(@RequestBody MappingRequest req) {
        return R.ok(mappingService.create(req));
    }

    /** 解绑。DELETE http://localhost:8080/mappings/1 */
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        return R.ok(mappingService.delete(id));
    }
}
