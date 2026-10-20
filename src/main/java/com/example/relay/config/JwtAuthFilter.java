package com.example.relay.config;

import com.example.relay.common.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 每个请求进来先看一眼 Authorization 头里有没有合法 token。
 *
 * <p>这个过滤器<b>不负责拒绝请求</b>，只负责「认人」：
 * token 合法就把用户名和角色放进 SecurityContext，不合法就什么都不做。
 * 放不放行由后面的 SecurityFilterChain 按路径规则决定 ——
 * 未认证的请求走到需要认证的接口时，由 Security 统一返回 401。
 *
 * <p>这样分开的好处：放行清单只有一处（SecurityConfig），
 * 过滤器和放行规则不会两处打架。
 *
 * <p>继承 OncePerRequestFilter：保证一次请求只跑一次，
 * 内部转发（forward）不会重复解析。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Claims claims = jwtUtil.parse(header.substring(BEARER_PREFIX.length()));
                String role = claims.get("role", String.class);

                // Spring Security 要求角色名带 ROLE_ 前缀；这里存的角色是 ADMIN / USER
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(), null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception ignored) {
                // 签名错误 / 过期 / 格式不对 —— 不设置认证即可，
                // 需要登录的接口会被 Security 挡成 401
            }
        }

        chain.doFilter(request, response);
    }
}
