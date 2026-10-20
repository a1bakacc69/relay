package com.example.relay.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Shopify webhook 验签。
 *
 * Shopify 发送 webhook 时，用双方共享的 API secret key 对「请求体原始字节」
 * 做 HMAC-SHA256，把结果 base64 后放进 X-Shopify-Hmac-Sha256 头。
 * 我们按同样算法算一遍，一致才放行。
 *
 * 入参必须是原始字节，不能是反序列化后的对象 —— 哈希对字节敏感，
 * JSON 重新序列化后字节会变，签名必然对不上。
 */
@Component
public class ShopifyWebhookVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    // 构造时固定下来，避免每次请求重复转码
    private final byte[] secret;

    // 冒号后的空串是默认值：没配密钥时不报错，但验签一律失败（安全默认）
    public ShopifyWebhookVerifier(@Value("${shopify.api.secret:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean verify(byte[] rawBody, String hmacHeader) {
        if (secret.length == 0 || hmacHeader == null || hmacHeader.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] computed = mac.doFinal(rawBody);
            byte[] provided = Base64.getDecoder().decode(hmacHeader);

            // 常量时间比较：不按「第几个字节不同」提前返回，避免时序侧信道
            return MessageDigest.isEqual(computed, provided);
        } catch (Exception e) {
            // 头不是合法 base64、算法不可用等，一律视为验签失败
            return false;
        }
    }
}
