package com.example.relay.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 签发和校验 JWT。
 *
 * <p>JWT 分三段：header.payload.signature，用点连起来。
 * 前两段只是 Base64，<b>谁都能解开看</b> —— 所以里面只放用户名和角色，
 * 绝不放密码之类的秘密。安全性全靠第三段签名：没有密钥就伪造不出合法签名。
 *
 * <p><b>为什么用 HMAC-SHA256 而不是 RSA：</b>
 * 只有一个后端签发、同一批后端校验，对称密钥够用；RSA 要管一对公私钥，这里没必要。
 * 代价是密钥泄露就能伪造 token，所以它跟数据库口令一样只走环境变量。
 */
@Component
public class JwtUtil {

    /** HS256 要求密钥至少 256 bit，即 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${jwt.secret:}") String secret,
                   @Value("${jwt.expire-hours:24}") long expireHours) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            // 宁可启动失败也不要悄悄用一个弱密钥 —— 那等于没有签名保护。
            // 本地开发填在 config/application.properties，线上走 JWT_SECRET 环境变量
            throw new IllegalStateException(
                    "jwt.secret 至少需要 " + MIN_SECRET_BYTES + " 个字节（当前 "
                            + bytes.length + " 字节）。本地填 config/application.properties，"
                            + "线上配置 JWT_SECRET 环境变量");
        }

        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMillis = expireHours * 3600_000L;
    }

    /** 登录成功后签发。sub 放用户名，另外塞一个 role 供后续鉴权 */
    public String generate(String username, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 校验签名并取出载荷。
     *
     * <p>签名不对、token 过期、格式损坏都会抛异常 —— 调用方一律当作「未登录」处理，
     * 不区分具体原因，免得给攻击者透露信息。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
