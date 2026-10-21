package com.example.relay.Controller;

import com.example.relay.common.R;
import com.example.relay.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 兜底同步：由 Vercel Cron 定时调用，把 webhook 可能漏掉的订单补进来。
 *
 * <p><b>为什么需要它</b>：webhook 是平台「尽力而为」的推送 —— 我们的容器冷启动、
 * 平台侧 5 秒超时、网络抖动，任何一环出问题这一单就永远不会进来，而且没有任何东西
 * 会提醒我们漏了。webhook 负责「快」，定时轮询负责「最终一定会到」，
 * 两者叠加才叫最终一致。
 *
 * <p><b>为什么不新增增量查询</b>：直接复用拉单那条路（全量拉 + 两层幂等），
 * 重复跑不会写重。订单量级小的时候，多拉几次比维护一套增量水位线可靠得多。
 *
 * <p><b>安全</b>：Vercel Cron 会带上 {@code Authorization: Bearer <CRON_SECRET>}。
 * 没配密钥就直接拒绝（fail closed）—— 这个接口真的会去打 Shopify 并写库，
 * 不能对公网敞开。
 */
@RestController
@RequestMapping("/cron")
public class CronController {

    private static final Logger log = LoggerFactory.getLogger(CronController.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private final OrderService orderService;

    @Value("${cron.secret:}")
    private String cronSecret;

    public CronController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * GET /cron/sync-orders —— Vercel Cron 走 GET，不支持自定义请求头，
     * 所以密钥走标准的 Authorization: Bearer。
     */
    @GetMapping("/sync-orders")
    public ResponseEntity<R<String>> syncOrders(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        // 没配密钥等于这把锁没装上，宁可拒绝也不裸奔
        if (cronSecret == null || cronSecret.isBlank()) {
            log.error("cron.secret 未配置，兜底同步拒绝执行");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(R.error(403, "cron.secret 未配置"));
        }

        if (authorization == null
                || !authorization.startsWith(BEARER_PREFIX)
                || !constantTimeEquals(authorization.substring(BEARER_PREFIX.length()), cronSecret)) {
            log.warn("兜底同步：密钥不匹配，已拒绝");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(R.error(401, "未授权"));
        }

        long start = System.currentTimeMillis();
        try {
            String summary = orderService.pullOrders();
            log.info("兜底同步完成，耗时 {} ms：{}",
                    System.currentTimeMillis() - start, summary.replace("\n", "；"));
            return ResponseEntity.ok(R.ok(summary));
        } catch (Exception e) {
            // 必须回 500：回 200 会让 Vercel 和任何监控都认为这次执行是成功的
            log.error("兜底同步失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.error(500, "兜底同步失败：" + e.getMessage()));
        }
    }

    /** 逐字节比较，不因为「第几位开始不一样」而提前返回 */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}